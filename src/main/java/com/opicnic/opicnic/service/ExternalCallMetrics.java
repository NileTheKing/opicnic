package com.opicnic.opicnic.service;

import com.opicnic.opicnic.exception.ExternalCallException;
import com.opicnic.opicnic.exception.ExternalCallException.Reason;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 외부 API(Groq) 호출 RED 계측. 2026-07-31·08-31 모델 소멸로 채점이 100% 실패했는데 JVM·HTTP·HikariCP
// 지표는 전부 정상이었다 — 외부 호출을 세는 지표가 없어서다. Timer 하나가 count(Rate) + outcome 태그(Errors)
// + 분위수(Duration)를 준다. 실제 경로와 mock 경로 둘 다 통과시켜 S2 호출 증폭을 지표 나눗셈으로 잰다.
//
//   opicnic_external_call_seconds{provider="groq", kind="stt|score|tag", outcome="ok|429|5xx|timeout|error"}
//
// 모든 외부 AI 호출이 record()를 지나므로, 클라이언트 예외를 우리 예외(ExternalCallException)로 옮기는 경계도 여기다.
// 클라이언트·공급자별 예외 모양은 이 파일만 안다.
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
            throw translate(e);
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
    // 채점/태깅은 Spring AI ChatModel을 거치며 자동 설정의 오류 처리기(SpringAiRetryAutoConfiguration)가
    // 상태 코드를 메시지 문자열에만 담은 Spring AI 예외로 바꿔 던진다(원인 체인 없음, 상태 코드 필드 없음).
    // 운영 메시지는 "HTTP 429 - {...}"이다. 2026-09-23까지는 "429"로 시작하는지만 봐서 실제 LLM 429·5xx를
    // 전부 error로 셌다(테스트가 상상한 형식 "429 - ..."을 넣어 통과). 예외 타입은 설정(on-http-codes)에 따라
    // 바뀌므로 보지 않고, 메시지 앞의 상태 코드만 본다. RetryUtils 기본 처리기 형식("429 - ...")도 받는다.
    static String outcomeOf(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof ResourceAccessException || cause instanceof SocketTimeoutException) {
                return "timeout";
            }
            Integer status = statusOf(cause);
            if (status != null) {
                if (status == 429) return "429";
                if (status >= 500) return "5xx";
                return "error";
            }
            cause = cause.getCause();
        }
        return "error";
    }

    // HTTP 응답·타임아웃에서 온 실패만 옮긴다. 응답 파싱 실패(InvalidModelOutputException) 같은 나머지는 그대로 둔다
    static RuntimeException translate(RuntimeException e) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof ResourceAccessException || c instanceof SocketTimeoutException) {
                return new ExternalCallException(Reason.UNAVAILABLE, e.getMessage(), e);
            }
            Integer status = statusOf(c);
            if (status != null) return new ExternalCallException(reasonOf(status), e.getMessage(), e);
        }
        return e;
    }

    private static Reason reasonOf(int status) {
        if (status == 429) return Reason.RATE_LIMITED;
        if (status >= 500) return Reason.UNAVAILABLE;
        if (status == 400 || status == 413 || status == 415 || status == 422) return Reason.BAD_REQUEST;
        return Reason.PROVIDER_ERROR;
    }

    private static final Pattern SPRING_AI_STATUS = Pattern.compile("^(?:HTTP )?(\\d{3}) - ");

    // 이 예외 하나만 본다(원인 체인은 호출하는 쪽이 돈다). HTTP 응답에서 온 예외가 아니면 null
    static Integer statusOf(Throwable e) {
        if (e instanceof HttpStatusCodeException http) {
            return http.getStatusCode().value();
        }
        if ((e instanceof NonTransientAiException || e instanceof TransientAiException) && e.getMessage() != null) {
            Matcher m = SPRING_AI_STATUS.matcher(e.getMessage());
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return null;
    }
}
