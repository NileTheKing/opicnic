package com.opicnic.opicnic.service;

import com.opicnic.opicnic.exception.ExternalCallException.Reason;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

// 상태 코드 판정이 기대는 메시지 형식을 추측하지 않고, 운영과 같은 Spring AI 오류 처리기를 직접 돌려서 받는다.
// 2026-09-23: 테스트가 상상한 형식("429 - ...")을 넣어 실제 형식("HTTP 429 - ...")을 못 알아보는 버그가 통과했다.
// Spring AI 버전을 올려 형식이 바뀌면 여기서 깨진다.
class SpringAiErrorFormatTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class))
            .withPropertyValues("spring.ai.retry.max-attempts=1", "spring.ai.retry.on-http-codes=429");

    private static Throwable handle(ResponseErrorHandler handler, HttpStatus status, String body) {
        var response = new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status);
        return catchThrowable(() -> handler.handleError(null, null, response));
    }

    @Test
    void realHandler_429_isTransientAndDetectedAs429() {
        runner.run(ctx -> {
            Throwable e = handle(ctx.getBean(ResponseErrorHandler.class), HttpStatus.TOO_MANY_REQUESTS,
                    "{\"error\":{\"code\":\"rate_limit_exceeded\"}}");
            assertThat(e).isInstanceOf(TransientAiException.class);
            assertThat(e.getMessage()).startsWith("HTTP 429 - ");
            assertThat(ExternalCallMetrics.outcomeOf(e)).isEqualTo("429");
            assertThat(ExternalCallMetricsTest.reasonOf((RuntimeException) e)).isEqualTo(Reason.RATE_LIMITED);
        });
    }

    @Test
    void realHandler_5xxAnd4xx() {
        runner.run(ctx -> {
            ResponseErrorHandler handler = ctx.getBean(ResponseErrorHandler.class);
            assertThat(ExternalCallMetrics.outcomeOf(handle(handler, HttpStatus.SERVICE_UNAVAILABLE, "down"))).isEqualTo("5xx");
            assertThat(ExternalCallMetrics.outcomeOf(handle(handler, HttpStatus.NOT_FOUND, "no model"))).isEqualTo("error");
            // 2026-10-08: 채점·태깅의 요청 거절이 일시적으로 분류돼 30분간 재시도되던 버그 — 실제 처리기 형식으로 고정
            assertThat(ExternalCallMetricsTest.reasonOf((RuntimeException) handle(handler, HttpStatus.BAD_REQUEST, "bad request"))).isEqualTo(Reason.BAD_REQUEST);
            assertThat(ExternalCallMetricsTest.reasonOf((RuntimeException) handle(handler, HttpStatus.UNPROCESSABLE_ENTITY, "unprocessable"))).isEqualTo(Reason.BAD_REQUEST);
            assertThat(ExternalCallMetricsTest.reasonOf((RuntimeException) handle(handler, HttpStatus.NOT_FOUND, "no model"))).isEqualTo(Reason.PROVIDER_ERROR);
        });
    }
}
