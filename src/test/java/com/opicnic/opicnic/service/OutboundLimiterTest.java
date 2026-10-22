package com.opicnic.opicnic.service;

import com.opicnic.opicnic.exception.ExternalCallException;
import com.opicnic.opicnic.exception.ExternalCallException.Reason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboundLimiterTest {

    @AfterEach
    void resetProvider() {
        MockProvider.configure(false, 0);
        MockProvider.configureLimits(0, 0, 1.0, false);
    }

    private static ExternalCallException rateLimited(Duration retryAfter) {
        return new ExternalCallException(Reason.RATE_LIMITED, "429", null, retryAfter);
    }

    @Test
    @DisplayName("기본(off): 아무것도 하지 않고 호출을 그대로 통과시킨다. 예외도 그대로")
    void offIsPassThrough() {
        var limiter = OutboundLimiter.off();
        assertThat(limiter.mode(ProviderKind.STT)).isEqualTo(OutboundLimiter.Mode.OFF);
        assertThat(limiter.mode(ProviderKind.LLM)).isEqualTo(OutboundLimiter.Mode.OFF);
        assertThat(limiter.call(ProviderKind.STT, () -> "ok")).isEqualTo("ok");
        var e = rateLimited(null);
        assertThatThrownBy(() -> limiter.call(ProviderKind.LLM, () -> { throw e; })).isSameAs(e);
        // 한도를 넘는 속도로 불러도 기다리지 않는다
        long t0 = System.nanoTime();
        for (int i = 0; i < 2000; i++) limiter.call(ProviderKind.STT, () -> "x");
        assertThat(Duration.ofNanos(System.nanoTime() - t0)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("모드 이름 파싱: 기본 off, 모르는 값은 거절")
    void modeParsing() {
        assertThat(OutboundLimiter.Mode.parse(null)).isEqualTo(OutboundLimiter.Mode.OFF);
        assertThat(OutboundLimiter.Mode.parse("AIMD")).isEqualTo(OutboundLimiter.Mode.AIMD);
        assertThatThrownBy(() -> OutboundLimiter.Mode.parse("x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fixed: 분당 한도를 넘는 속도로 부르면 토큰이 찰 때까지 기다린다")
    void fixedWaitsForTokens() {
        var limiter = new OutboundLimiter(OutboundLimiter.Mode.FIXED, 600, 600, null);   // 초당 10, 버스트 10
        AtomicInteger calls = new AtomicInteger();
        long t0 = System.nanoTime();
        for (int i = 0; i < 15; i++) limiter.call(ProviderKind.LLM, calls::incrementAndGet);
        long ms = Duration.ofNanos(System.nanoTime() - t0).toMillis();
        assertThat(calls.get()).isEqualTo(15);
        assertThat(ms).isGreaterThan(300L);   // 버스트 10개 뒤 5개는 초당 10개 속도로 약 0.5초
        assertThat(ms).isLessThan(2000L);
    }

    @Test
    @DisplayName("aimd: 429를 받으면 속도가 절반, 연속 429는 1초에 한 번만 줄이고, 바닥은 한도의 10%")
    void aimdHalvesOnRateLimit() {
        var limiter = new OutboundLimiter(OutboundLimiter.Mode.AIMD, 6000, 6000, null);
        try {
            assertThat(limiter.currentRatePerMinute(ProviderKind.STT)).isEqualTo(6000);
            for (int i = 0; i < 5; i++) {
                assertThatThrownBy(() -> limiter.call(ProviderKind.STT, () -> { throw rateLimited(null); }))
                        .isInstanceOf(ExternalCallException.class);
            }
            assertThat(limiter.currentRatePerMinute(ProviderKind.STT)).isEqualTo(3000);   // 5번 받아도 한 번만 줄었다
            assertThat(limiter.currentRatePerMinute(ProviderKind.LLM)).isEqualTo(6000);   // 종류별로 따로
            // 5xx는 속도를 건드리지 않는다
            assertThatThrownBy(() -> limiter.call(ProviderKind.LLM, () -> { throw new ExternalCallException(Reason.UNAVAILABLE, "503", null); }))
                    .isInstanceOf(ExternalCallException.class);
            assertThat(limiter.currentRatePerMinute(ProviderKind.LLM)).isEqualTo(6000);
        } finally {
            limiter.shutdown();
        }
    }

    @Test
    @DisplayName("종류마다 방식을 따로: STT는 꺼짐, LLM만 aimd면 429에 LLM만 줄고 STT는 기다리지 않는다")
    void modesArePerKind() {
        var limiter = new OutboundLimiter(OutboundLimiter.Mode.OFF, OutboundLimiter.Mode.AIMD, 60, 6000, null);
        try {
            assertThat(limiter.mode(ProviderKind.STT)).isEqualTo(OutboundLimiter.Mode.OFF);
            assertThat(limiter.mode(ProviderKind.LLM)).isEqualTo(OutboundLimiter.Mode.AIMD);
            assertThat(limiter.usesHeaders()).isFalse();
            assertThatThrownBy(() -> limiter.call(ProviderKind.LLM, () -> { throw rateLimited(null); }))
                    .isInstanceOf(ExternalCallException.class);
            assertThat(limiter.currentRatePerMinute(ProviderKind.LLM)).isEqualTo(3000);
            // STT 한도는 분당 60이지만 꺼져 있어 한꺼번에 불러도 기다리지 않는다
            long t0 = System.nanoTime();
            for (int i = 0; i < 200; i++) limiter.call(ProviderKind.STT, () -> "x");
            assertThat(Duration.ofNanos(System.nanoTime() - t0)).isLessThan(Duration.ofSeconds(1));
            assertThat(new OutboundLimiter(OutboundLimiter.Mode.HEADER, OutboundLimiter.Mode.FIXED, 60, 60, null).usesHeaders()).isTrue();
        } finally {
            limiter.shutdown();
        }
    }

    @Test
    @DisplayName("header: 남은 수 / 리셋까지 시간으로 속도를 맞춘다. 리셋 0이면 원래 한도, 429면 Retry-After 동안 멈춘다")
    void headerPacesFromResponseHeaders() {
        var limiter = new OutboundLimiter(OutboundLimiter.Mode.HEADER, 6000, 6000, null);
        // 남은 50, 리셋까지 10초 -> 초당 5 -> 분당 300
        limiter.call(ProviderKind.STT, () -> { OutboundLimiter.reportHeaders(50, 10); return "ok"; });
        assertThat(limiter.currentRatePerMinute(ProviderKind.STT)).isEqualTo(600);   // 바닥은 한도의 10% (분당 600)
        limiter.call(ProviderKind.LLM, () -> { OutboundLimiter.reportHeaders(2000, 10); return "ok"; });
        assertThat(limiter.currentRatePerMinute(ProviderKind.LLM)).isEqualTo(6000);   // 초당 200 -> 상한 100(원래 한도)
        // 바로 이어진 응답은 설정을 또 바꾸지 않는다(200ms에 한 번)
        limiter.call(ProviderKind.LLM, () -> { OutboundLimiter.reportHeaders(10, 10); return "ok"; });
        assertThat(limiter.currentRatePerMinute(ProviderKind.LLM)).isEqualTo(6000);
        try { Thread.sleep(250); } catch (InterruptedException ignored) { }
        limiter.call(ProviderKind.LLM, () -> { OutboundLimiter.reportHeaders(3000, 60); return "ok"; });
        assertThat(limiter.currentRatePerMinute(ProviderKind.LLM)).isEqualTo(3000);   // 초당 50
        try { Thread.sleep(250); } catch (InterruptedException ignored) { }
        limiter.call(ProviderKind.LLM, () -> { OutboundLimiter.reportHeaders(10, 0); return "ok"; });
        assertThat(limiter.currentRatePerMinute(ProviderKind.LLM)).isEqualTo(6000);   // 리셋 0 = 버킷이 가득 참 = 원래 한도
    }

    @Test
    @DisplayName("header: 429의 Retry-After 동안 다음 호출이 멈춰 있다")
    void headerPausesForRetryAfter() {
        var limiter = new OutboundLimiter(OutboundLimiter.Mode.HEADER, 6000, 6000, null);
        assertThatThrownBy(() -> limiter.call(ProviderKind.STT, () -> { throw rateLimited(Duration.ofMillis(600)); }))
                .isInstanceOf(ExternalCallException.class);
        long t0 = System.nanoTime();
        limiter.call(ProviderKind.STT, () -> "ok");
        assertThat(Duration.ofNanos(System.nanoTime() - t0).toMillis()).isGreaterThanOrEqualTo(500L);
    }

    @Test
    @DisplayName("가짜 제공자: 종류별 분당 한도(버스트 = 한도/60 x 5)를 넘으면 즉시 429, 용량 비율이 한도를 줄인다")
    void mockProviderPerKindBucket() {
        MockProvider.configureLimits(600, 0, 1.0, false);   // STT 초당 10, 버스트 50. LLM 무제한
        int ok = 0, rejected = 0;
        for (int i = 0; i < 80; i++) {
            try { MockProvider.admit(ProviderKind.STT); ok++; }
            catch (HttpClientErrorException e) { assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS); rejected++; }
        }
        assertThat(ok).isBetween(50, 52);
        assertThat(rejected).isEqualTo(80 - ok);
        for (int i = 0; i < 500; i++) MockProvider.admit(ProviderKind.LLM);   // 다른 종류는 영향 없음

        MockProvider.configureLimits(600, 0, 0.4, false);   // 용량 40% -> 버스트 20으로 잘림
        ok = 0;
        for (int i = 0; i < 80; i++) {
            try { MockProvider.admit(ProviderKind.STT); ok++; } catch (HttpClientErrorException ignored) { }
        }
        assertThat(ok).isLessThanOrEqualTo(20);
    }

    @Test
    @DisplayName("가짜 제공자: headers=true일 때만 429에 Retry-After를 주고 응답 헤더를 제한기에 전한다. 기본은 한도·헤더 없음")
    void mockProviderHeadersOnlyWhenEnabled() {
        // 기본값: 한도 없음, 429 없음
        for (int i = 0; i < 1000; i++) MockProvider.admit(ProviderKind.STT);

        MockProvider.configureLimits(60, 0, 1.0, true);   // 초당 1, 버스트 5
        for (int i = 0; i < 5; i++) MockProvider.admit(ProviderKind.STT);
        HttpClientErrorException e = null;
        try { MockProvider.admit(ProviderKind.STT); } catch (HttpClientErrorException ex) { e = ex; }
        assertThat(e).isNotNull();
        HttpHeaders h = e.getResponseHeaders();
        assertThat(h.getFirst("Retry-After")).isEqualTo("1");
        assertThat(h.getFirst("x-ratelimit-remaining-requests")).isEqualTo("0");
        assertThat(h.getFirst("x-ratelimit-reset-requests")).isNotNull();

        MockProvider.configureLimits(120, 0, 1.0, false);   // 초당 2, 버스트 10 (한도가 바뀌면 가득 찬 상태로 시작)
        for (int i = 0; i < 10; i++) MockProvider.admit(ProviderKind.STT);
        try { MockProvider.admit(ProviderKind.STT); } catch (HttpClientErrorException ex) { e = ex; }
        assertThat(e.getResponseHeaders().getFirst("Retry-After")).isNull();
    }

    @Test
    @DisplayName("429 응답의 Retry-After가 ExternalCallException으로 옮겨진다. 없으면 null, 5xx는 해당 없음")
    void retryAfterIsTranslated() {
        var h = new HttpHeaders();
        h.set("Retry-After", "7");
        var with = HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "x", h, new byte[0], StandardCharsets.UTF_8);
        var without = HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "x", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
        var server = HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "x", h, new byte[0], StandardCharsets.UTF_8);
        assertThat(((ExternalCallException) ExternalCallMetrics.translate(with)).retryAfter()).isEqualTo(Duration.ofSeconds(7));
        assertThat(((ExternalCallException) ExternalCallMetrics.translate(without)).retryAfter()).isNull();
        assertThat(((ExternalCallException) ExternalCallMetrics.translate(server)).retryAfter()).isNull();
    }
}
