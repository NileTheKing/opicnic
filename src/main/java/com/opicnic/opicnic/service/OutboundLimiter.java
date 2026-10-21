package com.opicnic.opicnic.service;

import com.opicnic.opicnic.exception.ExternalCallException;
import com.opicnic.opicnic.exception.ExternalCallException.Reason;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.TokensInheritanceStrategy;
import io.github.bucket4j.local.LocalBucket;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

// 우리 쪽 속도 제한기. 외부 호출 직전에 종류별(STT, LLM) 토큰을 하나 받는다 — 제공자가 429를 주기 전에 우리가 보내는 속도를 줄인다.
// 429는 "너무 빨랐다"는 신호라 재시도로 풀면 호출만 는다(2026-09-26 재시도 폭주 실험). 이 제한기는 429를 대기 줄로 바꾼다.
// 대기 줄이 모자란 용량 자체를 만들어 주지는 못한다.
//
//   off    (기본) 아무것도 하지 않는다. 지금 동작과 같다.
//   fixed  설정한 분당 한도(stt-per-minute, llm-per-minute)로 고정. 제공자 용량이 줄어도 모른다.
//   aimd   429를 받으면 현재 속도를 절반(최소 한도의 10%). 1초 동안 429가 없으면 한도의 2%씩 올린다(상한 한도).
//          한 번 줄인 뒤 1초 안에 오는 429(이미 나간 호출의 것)는 또 줄이지 않는다.
//   header 응답 헤더(남은 수 / 리셋까지 초)로 속도를 맞춘다: 속도 = 남은 수 / 리셋까지 시간. 429면 Retry-After 동안 모두 멈춘다.
//
// 토큰이 없으면 가상 스레드에서 기다린다. 30초를 넘기면 일시적 실패(LOCAL_LIMIT)로 던져 워커가 백오프로 다시 집게 한다(제공자 문제가 아니므로 워커 서킷에는 세지 않는다).
// 토큰 버킷은 Bucket4j. 버스트는 1초분(한도/60).
@Component
@Slf4j
public class OutboundLimiter {

    public enum Mode {
        OFF, FIXED, AIMD, HEADER;

        static Mode parse(String s) {
            return switch (s == null ? "off" : s.trim().toLowerCase()) {
                case "off" -> OFF;
                case "fixed" -> FIXED;
                case "aimd" -> AIMD;
                case "header" -> HEADER;
                default -> throw new IllegalArgumentException("opicnic.outbound.limiter.mode: off|fixed|aimd|header (받은 값: " + s + ")");
            };
        }
    }

    // 제공자 응답 헤더에서 읽은 값. 호출한 스레드에 실려 있다가 제한기가 읽는다(가짜 제공자가 채운다)
    record Headers(int remaining, double resetSeconds) {}

    private static final ThreadLocal<Headers> HEADERS = new ThreadLocal<>();

    static void reportHeaders(int remaining, double resetSeconds) {
        HEADERS.set(new Headers(remaining, resetSeconds));
    }

    static final Duration MAX_WAIT = Duration.ofSeconds(30);
    private static final long ADJUST_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(200);

    private final Mode mode;
    private final Lane stt;
    private final Lane llm;
    private final ScheduledExecutorService ticker;

    // 꺼진 제한기 — 주입이 없는 곳(단위 테스트, 수동 생성)의 기본값
    public static OutboundLimiter off() {
        return new OutboundLimiter(Mode.OFF, 0, 0, null);
    }

    @Autowired
    public OutboundLimiter(@Value("${opicnic.outbound.limiter.mode:off}") String mode,
                           @Value("${opicnic.outbound.limiter.stt-per-minute:5000}") int sttPerMinute,
                           @Value("${opicnic.outbound.limiter.llm-per-minute:10000}") int llmPerMinute,
                           MeterRegistry registry) {
        this(Mode.parse(mode), sttPerMinute, llmPerMinute, registry);
    }

    OutboundLimiter(Mode mode, int sttPerMinute, int llmPerMinute, MeterRegistry registry) {
        this.mode = mode;
        this.stt = new Lane(sttPerMinute);
        this.llm = new Lane(llmPerMinute);
        if (mode == Mode.AIMD) {
            ticker = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "outbound-limiter-ticker");
                t.setDaemon(true);
                return t;
            });
            ticker.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
        } else {
            ticker = null;
        }
        if (mode != Mode.OFF) {
            log.info("[Limiter] mode={} stt={}/min llm={}/min", mode, sttPerMinute, llmPerMinute);
            if (registry != null) {
                registry.gauge("opicnic.outbound.limiter.rate", java.util.List.of(io.micrometer.core.instrument.Tag.of("kind", "stt")), stt, l -> l.ratePerMinute);
                registry.gauge("opicnic.outbound.limiter.rate", java.util.List.of(io.micrometer.core.instrument.Tag.of("kind", "llm")), llm, l -> l.ratePerMinute);
            }
        }
    }

    public Mode mode() {
        return mode;
    }

    public <T> T call(ProviderKind kind, Supplier<T> call) {
        if (mode == Mode.OFF) return call.get();
        Lane lane = kind == ProviderKind.STT ? stt : llm;
        lane.acquire();
        HEADERS.remove();
        try {
            T result = call.get();
            if (mode == Mode.HEADER) lane.onHeaders(HEADERS.get(), null, false);
            return result;
        } catch (ExternalCallException e) {
            if (e.reason() == Reason.RATE_LIMITED) {
                if (mode == Mode.AIMD) lane.onRateLimited();
                if (mode == Mode.HEADER) lane.onHeaders(HEADERS.get(), e.retryAfter(), true);
            }
            throw e;
        } finally {
            HEADERS.remove();
        }
    }

    private void tick() {
        long now = System.nanoTime();
        for (Lane lane : new Lane[]{stt, llm}) {
            if (now - lane.last429Nanos >= TimeUnit.SECONDS.toNanos(1)) {
                lane.setRate(lane.ratePerMinute + Math.max(1, lane.base * 2 / 100));
            }
        }
    }

    // 테스트·진단용
    long currentRatePerMinute(ProviderKind kind) {
        return (kind == ProviderKind.STT ? stt : llm).ratePerMinute;
    }

    @PreDestroy
    void shutdown() {
        if (ticker != null) ticker.shutdownNow();
    }

    private static final class Lane {
        final long base;                 // 원래 한도(분당). 0 이하면 제한 없음
        final LocalBucket bucket;
        volatile long ratePerMinute;
        volatile long pausedUntilNanos;
        volatile long last429Nanos = Long.MIN_VALUE / 2;
        volatile long lastDecreaseNanos = Long.MIN_VALUE / 2;
        volatile long lastAdjustNanos = Long.MIN_VALUE / 2;

        Lane(long base) {
            this.base = base;
            this.ratePerMinute = base;
            this.bucket = base <= 0 ? null : io.github.bucket4j.Bucket.builder().addLimit(limit(base)).build();
        }

        private static Bandwidth limit(long perMinute) {
            return Bandwidth.builder().capacity(Math.max(1, perMinute / 60)).refillGreedy(perMinute, Duration.ofMinutes(1)).build();
        }

        void acquire() {
            if (bucket == null) return;
            long deadline = System.nanoTime() + MAX_WAIT.toNanos();
            try {
                long pause = pausedUntilNanos - System.nanoTime();
                if (pause > 0) {
                    if (pause > MAX_WAIT.toNanos()) throw timeout();
                    TimeUnit.NANOSECONDS.sleep(pause);
                }
                long left = deadline - System.nanoTime();
                if (left <= 0 || !bucket.asBlocking().tryConsume(1, Duration.ofNanos(left))) throw timeout();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ExternalCallException(Reason.LOCAL_LIMIT, "속도 제한기 대기 중 인터럽트", e);
            }
        }

        private static ExternalCallException timeout() {
            return new ExternalCallException(Reason.LOCAL_LIMIT, "속도 제한기 대기 " + MAX_WAIT.toSeconds() + "초 초과", null);
        }

        void setRate(long perMinute) {
            if (bucket == null) return;
            long floor = Math.max(1, base / 10);
            long next = Math.min(base, Math.max(floor, perMinute));
            if (next == ratePerMinute) return;
            ratePerMinute = next;
            bucket.replaceConfiguration(BucketConfiguration.builder().addLimit(limit(next)).build(), TokensInheritanceStrategy.AS_IS);
        }

        // AIMD: 429 한 번에 절반. 같은 폭주에서 온 429들이 연달아 바닥까지 내리지 않게 1초에 한 번만 줄인다
        void onRateLimited() {
            long now = System.nanoTime();
            last429Nanos = now;
            if (now - lastDecreaseNanos >= TimeUnit.SECONDS.toNanos(1)) {
                lastDecreaseNanos = now;
                setRate(ratePerMinute / 2);
            }
        }

        // 헤더: 속도 = 남은 수 / 리셋까지 시간(초). 리셋이 0이면 버킷이 가득 찬 것이라 원래 한도
        void onHeaders(Headers h, Duration retryAfter, boolean rateLimited) {
            if (bucket == null) return;
            if (rateLimited && retryAfter != null) {
                long until = System.nanoTime() + retryAfter.toNanos();
                if (until - pausedUntilNanos > 0) pausedUntilNanos = until;
            }
            if (h == null) return;
            long now = System.nanoTime();
            if (!rateLimited && now - lastAdjustNanos < ADJUST_INTERVAL_NANOS) return;   // 응답마다 설정을 바꾸지 않는다
            lastAdjustNanos = now;
            double perSecond = h.resetSeconds() > 0 ? h.remaining() / h.resetSeconds() : base / 60.0;
            setRate(Math.round(perSecond * 60));
        }
    }
}
