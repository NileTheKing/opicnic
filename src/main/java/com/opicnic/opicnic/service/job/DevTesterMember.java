package com.opicnic.opicnic.service.job;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.enums.Role;
import com.opicnic.opicnic.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// dev 전용. DevPracticeController가 만든 memberId=null attempt(k6·측정 스크립트)를 비동기 경로로 제출하면
// 잡과 FeedbackResult에 주인이 필요하다. FeedbackResult.member를 nullable로 풀지 않고, dev 프로파일에서만
// 존재하는 고정 회원 하나를 쓴다 — 운영 DB에는 생기지 않고, 스키마 원칙도 안 깨진다.
// S1 시나리오(제출 → kill/restart → DB에서 15문항 완료 확인)가 이 회원 덕에 성립한다.
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevTesterMember {

    static final String PROVIDER = "dev";
    static final String PROVIDER_ID = "tester";

    private final MemberRepository memberRepository;

    public Member get() {
        return memberRepository.findByProviderAndProviderId(PROVIDER, PROVIDER_ID)
                .orElseGet(() -> memberRepository.save(Member.builder()
                        .provider(PROVIDER).providerId(PROVIDER_ID).nickname("dev-tester").role(Role.USER).build()));
    }
}
