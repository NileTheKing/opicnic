package com.opicnic.opicnic.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

// 2026-09-18: 채점/태깅은 Spring AI ChatModel을 거치면서 429가 Spring AI 예외("HTTP 429 - …")로
// 바뀌고 원인 체인이 없다. 예외 타입만 보던 isRateLimited()는 이걸 놓쳐서 실제 LLM 429가 긴 백오프
// 분기를 못 탔다. STT(RestClient 직접, HttpClientErrorException)와 LLM 두 경로 다 잡히는지 고정한다.
class FeedbackServiceRateLimitDetectionTest {

    private static boolean isRateLimited(Throwable e) {
        return (boolean) ReflectionTestUtils.invokeMethod(FeedbackService.class, "isRateLimited", e);
    }

    @Test
    void sttPath_httpClientErrorException429_isRateLimited() {
        var e = HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                HttpHeaders.EMPTY, "rate limit".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        assertThat(isRateLimited(e)).isTrue();
    }

    @Test
    void llmPath_nonTransientAiException429_isRateLimited() {
        var e = new NonTransientAiException("429 - {\"error\":{\"message\":\"Rate limit reached for model\"}}");
        assertThat(isRateLimited(e)).isTrue();
    }

    @Test
    void llmPath_realAutoConfigFormat_isRateLimited() {
        assertThat(isRateLimited(new NonTransientAiException(ExternalCallMetricsTest.REAL_LLM_429))).isTrue();
        assertThat(isRateLimited(new TransientAiException(ExternalCallMetricsTest.REAL_LLM_429))).isTrue();
    }

    @Test
    void llmPath_nonTransientAiException400_isNotRateLimited() {
        var e = new NonTransientAiException("HTTP 400 - bad request");
        assertThat(isRateLimited(e)).isFalse();
    }

    @Test
    void wrappedInRuntimeException_stillDetected() {
        var e = new RuntimeException("wrapper", new NonTransientAiException("429 - x"));
        assertThat(isRateLimited(e)).isTrue();
    }
}
