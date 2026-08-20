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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final MemberRepository memberRepository; // Repository를 직접 사용

    // DATA-02: 이 메서드 전체를 하나의 @Transactional로 묶으면(예전 방식), 아래에서 잡는
    // DataIntegrityViolationException이 있어도 Spring이 그 트랜잭션을 이미 rollback-only로
    // 표시해버려 이후 재조회가 커밋 시점에 UnexpectedRollbackException으로 깨진다. 그래서
    // 여기엔 @Transactional을 두지 않는다 — findByProviderAndProviderId/save 각각 Spring Data
    // JPA가 자체적으로 부여하는 개별 트랜잭션으로 실행되고, 하나가 실패해도 다음 재조회는
    // 깨끗한 새 트랜잭션에서 시작된다.
    @Override
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

//            log.info("nickname: {}", nickname);
//            log.info("providerId: {}", providerId);
//            log.info("registrationId: {}", registrationId);
//            log.info("attributes: {}", attributes);
//
//            log.info("All attributes from Kakao: {}", attributes);


            // provider + providerId 기준으로 기존 유저 조회
            Member member = findOrCreateMember(registrationId, providerId, nickname);

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

    // package-private: DATA-02 테스트가 실제 카카오 HTTP 왕복(super.loadUser()) 없이
    // find-or-create 경쟁 처리 로직만 직접 검증할 수 있도록.
    Member findOrCreateMember(String provider, String providerId, String nickname) {
        return memberRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> createMember(provider, providerId, nickname));
    }

    Member createMember(String provider, String providerId, String nickname) {
        Member newMember = Member.builder()
                .provider(provider)
                .providerId(providerId)
                .nickname(nickname)
                .role(Role.USER)
                .build();
        // DATA-02: NotificationSetting이 FK 소유 쪽(@JoinColumn)이므로, 그쪽에도 member를
        // 직접 설정해야 실제로 member_id가 채워진다. member.setNotificationSetting(...)만 하면
        // (예전 코드) cascade로 행 자체는 생기지만 member_id가 null인 채로 저장되어, 이후
        // MyPageController가 findByMember로 못 찾고 매번 새 orphan row를 하나 더 만들었다.
        NotificationSetting notificationSetting = new NotificationSetting();
        notificationSetting.setMember(newMember);
        newMember.setNotificationSetting(notificationSetting);

        try {
            return memberRepository.save(newMember);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 동시 OAuth 콜백이 위 findByProviderAndProviderId를 둘 다 통과한 경우의 최종
            // 방어선(provider+providerId 유니크 제약). 이미 다른 요청이 만든 회원을 그대로 쓴다.
            return memberRepository.findByProviderAndProviderId(provider, providerId)
                    .orElseThrow(() -> e);
        }
    }
}