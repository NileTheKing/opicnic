package com.opicnic.opicnic.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

// COST-01 회귀 테스트: /analytics/coaching은 GET(조회)과 POST(리포트 생성, 실제 LLM 호출)를
// 같은 경로로 공유하므로 GET은 소비하지 않아야 한다. 문항 수 기준 소비(자기소개 제외 등)는
// PracticeAttemptApiControllerCostGuardTest에서 검증한다 — 그 로직은 검증 실패 시 한도를
// 낭비하지 않도록 인터셉터가 아니라 컨트롤러(검증 통과 후)로 옮겨졌다.
class RateLimitInterceptorTest {

    @Test
    void getRequestsDoNotConsumeBucket() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(new RateLimiterService(new StandardEnvironment()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/analytics/coaching");
        MockHttpServletResponse response = new MockHttpServletResponse();

        for (int i = 0; i < 50; i++) {
            boolean allowed = interceptor.preHandle(request, response, new Object());
            assertThat(allowed).isTrue();
        }
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void postRequestsConsumeOneUnitAndEventuallyGetLimited() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(new RateLimiterService(new StandardEnvironment()));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/analytics/coaching");

        int allowedCount = 0;
        for (int i = 0; i < 20; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            if (interceptor.preHandle(request, response, new Object())) {
                allowedCount++;
            } else {
                assertThat(response.getStatus()).isEqualTo(429);
            }
        }
        // RateLimiterService 버킷 용량 15 -> 20번 시도 중 15번만 허용
        assertThat(allowedCount).isEqualTo(RateLimiterService.CAPACITY_PER_HOUR);
    }
}
