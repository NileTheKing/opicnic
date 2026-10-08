package com.opicnic.opicnic.service.guest;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.NotificationSetting;
import com.opicnic.opicnic.domain.enums.Role;
import com.opicnic.opicnic.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GuestService {

    static final String NICKNAME = "게스트";

    private final MemberRepository memberRepository;
    private final GuestProperties properties;
    private final GuestSampleCopier sampleCopier;

    // 방문자마다 새 회원 — 다른 방문자의 녹음·답변이 섞이면 안 된다. 권한은 항상 USER(ADMIN 불가).
    @Transactional
    public Member createGuest() {
        Member guest = Member.builder()
                .provider(GuestProperties.PROVIDER)
                .providerId(UUID.randomUUID().toString())
                .nickname(NICKNAME)
                .role(Role.USER)
                .build();
        // CustomOAuth2UserService.createMember와 같다 — FK 소유 쪽에도 member를 직접 넣어야 member_id가 채워진다
        NotificationSetting notificationSetting = new NotificationSetting();
        notificationSetting.setMember(guest);
        guest.setNotificationSetting(notificationSetting);
        guest = memberRepository.save(guest);

        if (properties.getSampleMemberId() != null) {
            sampleCopier.copy(properties.getSampleMemberId(), guest, LocalDateTime.now());
        }
        return guest;
    }

    // 카카오 로그인(CustomOAuth2UserService)이 만드는 principal과 같은 모양: authority는 role 이름,
    // attributes에 provider/providerId, nameAttributeKey=providerId. 기존 컨트롤러가 전부 이걸로 회원을 찾는다.
    // registrationId "guest"는 등록된 OAuth2 클라이언트가 아니라 토큰 표지일 뿐이다(authorizedClient는 저장하지 않는다).
    public OAuth2AuthenticationToken toAuthentication(Member guest) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(guest.getRole().name()));
        OAuth2User principal = new DefaultOAuth2User(authorities,
                Map.of("provider", GuestProperties.PROVIDER,
                        "providerId", guest.getProviderId(),
                        "nickname", guest.getNickname()),
                "providerId");
        return new OAuth2AuthenticationToken(principal, authorities, GuestProperties.PROVIDER);
    }
}
