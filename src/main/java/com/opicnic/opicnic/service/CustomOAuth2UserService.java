package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.NotificationSetting;
import com.opicnic.opicnic.domain.enums.Role;
import com.opicnic.opicnic.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final MemberRepository memberRepository; // Repository를 직접 사용

    @Override
    @Transactional // 트랜잭션 처리!
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        try {
            OAuth2User oAuth2User = super.loadUser(userRequest);

            log.info("OAUTH2 custom user service called");

            String registrationId = userRequest.getClientRegistration().getRegistrationId(); // ex: "kakao"
            Map<String, Object> attributes = oAuth2User.getAttributes();

            // 카카오 사용자 정보 꺼내기
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

            String nickname = (String) profile.get("nickname");
            String providerId = attributes.get("id").toString(); // 카카오 고유 ID는 attributes의 "id" key

            log.info("nickname: {}", nickname);
            log.info("providerId: {}", providerId);
            log.info("registrationId: {}", registrationId);
            log.info("attributes: {}", attributes);

            log.info("All attributes from Kakao: {}", attributes);


            // provider + providerId 기준으로 기존 유저 조회
            Member member = memberRepository.findByProviderAndProviderId(registrationId, providerId)
                    .orElseGet(() -> {
                        // 없으면 새로 저장
                        Member newMember = Member.builder()
                                .provider(registrationId)
                                .providerId(providerId)
                                .nickname(nickname)
                                .role(Role.USER)
                                .build();
                        newMember.setNotificationSetting(new NotificationSetting()); // 알림 설정 초기화
                        memberRepository.save(newMember);  // 새 회원을 저장하고 저장된 객체를 반환
                        return newMember; // 새로 생성된 회원 객체 리턴
                    });

            Map<String, Object> customAttributes = new HashMap<>(attributes);
            customAttributes.put("providerId", providerId);
            customAttributes.put("provider", registrationId); // 혹은 registrationId]

            return new DefaultOAuth2User(
                    Collections.singleton(new SimpleGrantedAuthority(member.getRole().name())),
                    customAttributes,
                    "providerId"
            );
        } catch (Exception e) {
            log.error("Error loading user from OAuth2 provider", e);
            throw new OAuth2AuthenticationException (String.valueOf(e));
        }
    }
}