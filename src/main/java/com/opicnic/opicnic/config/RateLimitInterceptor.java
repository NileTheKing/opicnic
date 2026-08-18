package com.opicnic.opicnic.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// /analytics/coaching처럼 "요청 1건 = 실제 비용 1건"으로 단순 대응되는 경로에만 쓴다.
// /api/practice-attempts/*/answers[/retry]는 문항 수만큼 비용이 나고 사전 검증도 필요해서,
// 여기서 미리 소비하지 않고 PracticeAttemptApiController가 검증 통과 후 직접 RateLimiterService를
// 호출한다 — 인터셉터에서 먼저 소비해버리면 검증 실패로 끝날 요청도 한도를 깎아먹는다.
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // /analytics/coaching은 GET(페이지 조회)과 POST(리포트 생성 - 실제 LLM 호출)를 같은 경로로 공유한다.
        // GET 조회까지 소비하면 코칭 화면 새로고침 몇 번으로 한도가 소진되므로, 비용이 실제로 발생하는
        // POST만 소비 대상으로 한다.
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (rateLimiterService.tryConsume(1)) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.getWriter().write("시간당 문항 한도를 초과했습니다. 잠시 후 다시 시도해주세요.");
        return false;
    }
}
