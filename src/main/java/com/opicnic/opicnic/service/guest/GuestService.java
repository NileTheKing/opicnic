package com.opicnic.opicnic.service.guest;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.NotificationSetting;
import com.opicnic.opicnic.domain.SurveyProfile;
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.domain.enums.Role;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.service.SurveyTopicPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final GuestSampleSource sampleSource;
    private final SurveyProfileRepository surveyProfileRepository;

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

        Member saved = guest;
        sampleSource.get().ifPresent(sample -> sampleCopier.copy(sample, saved, LocalDateTime.now()));
        // 둘러보러 온 사람에게 2분 설정을 먼저 시키지 않는다 — 온보딩 "전체 선택"과 같은 기본값. 바꾸고 싶으면 마이페이지에서
        surveyProfileRepository.save(defaultProfile(guest));
        return guest;
    }

    static SurveyProfile defaultProfile(Member guest) {
        List<SurveyTopic> topics = new ArrayList<>(SurveyTopicPolicy.allTopics());
        topics.add(SurveyTopic.LIVING_ALONE);  // 온보딩이 거주 형태 주제를 자동으로 붙이는 것과 같게
        return SurveyProfile.builder()
                .member(guest)
                .occupationType(SurveyProfile.OccupationType.NO_WORK_EXPERIENCE)
                .residenceType(SurveyProfile.ResidenceType.ALONE)
                .targetGrade(SurveyProfile.TargetGrade.IH)
                .preferredDifficulty(SurveyDifficulty.LEVEL_5)
                .selectedTopics(topics)
                .build();
    }

    // 카카오 로그인(CustomOAuth2UserService)이 만드는 principal과 같은 모양: authority는 role 이름,
    // attributes에 provider/providerId, nameAttributeKey=providerId. 기존 컨트롤러가 전부 이걸로 회원을 찾는다.
    // registrationId "guest"는 등록된 OAuth2 클라이언트가 아니라 토큰 표지일 뿐이다(authorizedClient는 저장하지 않는다).
    public OAuth2AuthenticationToken toAuthentication(Member guest) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(guest.getRole().name()));
        OAuth2User principal = new DefaultOAuth2User(authorities,
                Map.of("provider", GuestProperties.PROVIDER,
                        "providerId", guest.getProviderId(),
                        "nickname", guest.getNickname(),
                        // 헤더·사이드바가 이름을 카카오 응답 모양(kakao_account.profile.nickname)으로 읽는다
                        "kakao_account", Map.of("profile", Map.of("nickname", guest.getNickname()))),
                "providerId");
        return new OAuth2AuthenticationToken(principal, authorities, GuestProperties.PROVIDER);
    }
}
