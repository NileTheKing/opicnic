package com.opicnic.opicnic.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

// FU-03 회귀 테스트: dev의 모든 익명(memberId=null) VU가 이름만 다른 "dev-loadtest"라는 하나의
// 유한 버킷(시간당 15)을 공유하던 문제. k6가 20~100 VU로 붙으면 최초 몇 요청 뒤 대부분 429가 났다.
// dev 프로파일 + attemptMemberId==null 조합만 소비 자체를 건너뛰어야(무제한) 하고, 그 외 조합
// (dev의 로그인 회원, production의 익명)은 기존 시간당 15문항 한도를 그대로 유지해야 한다.
class RateLimiterServiceDevProfileTest {

    @Test
    void activeDevProfileWithNullMemberIdNeverConsumesTheBucket() {
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.addActiveProfile("dev");
        RateLimiterService limiter = new RateLimiterService(devEnv);

        for (int i = 0; i < RateLimiterService.CAPACITY_PER_HOUR + 100; i++) {
            assertThat(limiter.tryConsume(1, null)).isTrue();
        }
    }

    // spring.profiles.default=dev라 활성 프로파일을 아무것도 지정하지 않은 실행도 dev로 판정돼야 한다.
    @Test
    void defaultDevProfileWithNullMemberIdNeverConsumesTheBucket() {
        MockEnvironment defaultDevEnv = new MockEnvironment();
        defaultDevEnv.setDefaultProfiles("dev"); // 활성 프로파일은 지정하지 않음
        RateLimiterService limiter = new RateLimiterService(defaultDevEnv);

        for (int i = 0; i < RateLimiterService.CAPACITY_PER_HOUR + 100; i++) {
            assertThat(limiter.tryConsume(1, null)).isTrue();
        }
    }

    @Test
    void activeDevProfileWithRealMemberIdKeepsNormalLimit() {
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.addActiveProfile("dev");
        RateLimiterService limiter = new RateLimiterService(devEnv);

        for (int i = 0; i < RateLimiterService.CAPACITY_PER_HOUR; i++) {
            assertThat(limiter.tryConsume(1, 42L)).isTrue();
        }
        assertThat(limiter.tryConsume(1, 42L)).isFalse();
    }

    @Test
    void productionProfileWithNullMemberIdKeepsNormalLimit() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.addActiveProfile("prod");
        RateLimiterService limiter = new RateLimiterService(prodEnv);

        for (int i = 0; i < RateLimiterService.CAPACITY_PER_HOUR; i++) {
            assertThat(limiter.tryConsume(1, null)).isTrue();
        }
        assertThat(limiter.tryConsume(1, null)).isFalse();
    }
}
