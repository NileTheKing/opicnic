package com.opicnic.opicnic.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // COST-01: 이전엔 존재하지 않는 경로(/practice/combo/feedback)를 가리키고 있어
        // 실제 비용이 발생하는 경로엔 limiter가 전혀 걸려있지 않았다.
        // /api/practice-attempts/*/answers[/retry]는 여기서 걸지 않는다 — 입력 검증(중복/파일
        // 크기 등)을 통과하기 전에 인터셉터가 먼저 한도를 소비해버리면, 결국 400으로 거부될
        // 요청도 한도를 깎아먹는다. 그 경로는 PracticeAttemptApiController가 검증 통과 후
        // 직접 RateLimiterService를 호출한다. GET 조회는 이 인터셉터가 자체적으로 건너뛴다.
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/analytics/coaching");
    }
}
