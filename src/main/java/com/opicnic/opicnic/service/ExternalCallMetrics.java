package com.opicnic.opicnic.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.util.function.Supplier;

// 외부 API(Groq) 호출 RED 계측. 2026-07-31·08-31 모델 소멸로 채점이 100% 실패했는데 JVM·HTTP·HikariCP
// 지표는 전부 정상이었다 — 외부 호출을 세는 지표가 없어서다. Timer 하나가 count(Rate) + outcome 태그(Errors)
// + 분위수(Duration)를 준다. 실제 경로와 mock 경로 둘 다 통과시켜 S2 호출 증폭을 지표 나눗셈으로 잰다.
//
//   opicnic_external_call_seconds{provider="groq", kind="stt|score|tag", outcome="ok|429|5xx|timeout|error"}
final class ExternalCallMetrics {

    static final String TIMER_NAME = "opicnic.external.call";

    private ExternalCallMetrics() {}

    static <T> T record(MeterRegistry registry, String kind, Supplier<T> call) {
        Timer.Sample sample = Timer.start(registry);
        String outcome = "ok";
        try {
            return call.get();
        } catch (RuntimeException e) {
            outcome = outcomeOf(e);
            throw e;
        } finally {
            sample.stop(Timer.builder(TIMER_NAME)
                    .tag("provider", "groq")
                    .tag("kind", kind)
                    .tag("outcome", outcome)
                    .publishPercentileHistogram()
                    .register(registry));
        }
    }

    // STT는 RestClient 직접 호출이라 HttpClientErrorException/HttpServerErrorException이 그대로 오지만,
    // 채점/태깅은 Spring AI ChatModel을 거치며 RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER가 4xx를
    // NonTransientAiException("429 - body"), 5xx를 TransientAiException("503 - body")으로 바꿔 던진다
    // (원인 체인 없음). 그래서 메시지 앞의 상태 코드도 본다.
    static String outcomeOf(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof HttpClientErrorException httpEx) {
                return httpEx.getStatusCode().value() == 429 ? "429" : "error";
            }
            if (cause instanceof HttpServerErrorException) {
                return "5xx";
            }
            if (cause instanceof ResourceAccessException || cause instanceof SocketTimeoutException) {
                return "timeout";
            }
            if (cause instanceof NonTransientAiException && startsWith(cause, "429")) {
                return "429";
            }
            if (cause instanceof TransientAiException && startsWith(cause, "5")) {
                return "5xx";
            }
            cause = cause.getCause();
        }
        return "error";
    }

    private static boolean startsWith(Throwable e, String prefix) {
        return e.getMessage() != null && e.getMessage().startsWith(prefix);
    }
}
