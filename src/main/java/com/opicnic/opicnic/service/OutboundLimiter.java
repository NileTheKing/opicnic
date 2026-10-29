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
// 방식은 종류마다 따로 고른다(stt-mode, llm-mode, 없으면 mode). 제공자마다 맞는 방식이 달라서다 — 남은 분당 한도를 헤더로 주는
// 제공자(OpenAI)는 header, 헤더가 없는 제공자(Gemini)는 aimd, 한도가 고정이고 알려진 무료 등급은 fixed.
//
// 혼잡 제한(congestion=true, 기본 false). 위 방식들은 "내 한도"(429·헤더)를 본다. 제공자 사정으로 받아주는 양이 줄 때는
// 429가 아니라 과부하 5xx(Gemini 503 overloaded, OpenAI 503 server_is_overloaded, Anthropic 529)가 온다. 상태 코드가 아니라
// "덜 보내면 나아지는가"로 나눈다: 1초 동안 5xx·타임아웃과 성공이 섞여 있으면 일부만 받아주는 것이니 혼잡 속도를 절반으로,
// 5xx가 없으면 한도의 2%씩 올린다. 전부 실패면 손대지 않는다 — 완전 장애는 서킷 몫이고, 여기서 줄이면 복구 뒤에도 바닥에서 천천히 올라온다.
// 실제로 보내는 속도 = min(한도 쪽 속도, 혼잡 속도). 두 신호를 한 속도에 섞으면 어느 쪽이 줄였는지 모르게 되어 따로 둔다.
// 같은 종류 호출이 최근 2초 안에 성공했으면 그 5xx는 CONGESTED로 바꿔 던진다 — 서킷이 과부하 5xx까지 세면 제한기가 반응하기 전에
// 열리고(빠른 거절이 창을 채운다), 열린 30초 동안 혼잡 속도가 다시 올라 닫히자마자 또 몰린다(2026-10-29 과부하 실험 B 첫 실행).
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
    static final long RECENT_SUCCESS_NANOS = TimeUnit.SECONDS.toNanos(2);

    private final Lane stt;
    private final Lane llm;
    private final ScheduledExecutorService ticker;

    // 꺼진 제한기 — 주입이 없는 곳(단위 테스트, 수동 생성)의 기본값
    public static OutboundLimiter off() {
        return new OutboundLimiter(Mode.OFF, 0, 0, null);
    }

    @Autowired
    public OutboundLimiter(@Value("${opicnic.outbound.limiter.stt-mode:${opicnic.outbound.limiter.mode:off}}") String sttMode,
                           @Value("${opicnic.outbound.limiter.llm-mode:${opicnic.outbound.limiter.mode:off}}") String llmMode,
                           @Value("${opicnic.outbound.limiter.stt-per-minute:5000}") int sttPerMinute,
                           @Value("${opicnic.outbound.limiter.llm-per-minute:10000}") int llmPerMinute,
                           @Value("${opicnic.outbound.limiter.congestion:false}") boolean congestion,
                           MeterRegistry registry) {
        this(Mode.parse(sttMode), Mode.parse(llmMode), sttPerMinute, llmPerMinute, congestion, registry);
    }

    OutboundLimiter(Mode mode, int sttPerMinute, int llmPerMinute, MeterRegistry registry) {
        this(mode, mode, sttPerMinute, llmPerMinute, registry);
    }

    OutboundLimiter(Mode sttMode, Mode llmMode, int sttPerMinute, int llmPerMinute, MeterRegistry registry) {
        this(sttMode, llmMode, sttPerMinute, llmPerMinute, false, registry);
    }

    OutboundLimiter(Mode sttMode, Mode llmMode, int sttPerMinute, int llmPerMinute, boolean congestion, MeterRegistry registry) {
        this.stt = new Lane(sttMode, sttPerMinute, congestion);
        this.llm = new Lane(llmMode, llmPerMinute, congestion);
        if (sttMode == Mode.AIMD || llmMode == Mode.AIMD || congestion) {
            ticker = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "outbound-limiter-ticker");
                t.setDaemon(true);
                return t;
            });
            ticker.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
        } else {
            ticker = null;
        }
        if (sttMode != Mode.OFF || llmMode != Mode.OFF || congestion) {
            log.info("[Limiter] stt={} {}/min, llm={} {}/min, congestion={}", sttMode, sttPerMinute, llmMode, llmPerMinute, congestion);
            if (registry != null) {
                registry.gauge("opicnic.outbound.limiter.rate", java.util.List.of(io.micrometer.core.instrument.Tag.of("kind", "stt")), stt, l -> l.ratePerMinute);
                registry.gauge("opicnic.outbound.limiter.rate", java.util.List.of(io.micrometer.core.instrument.Tag.of("kind", "llm")), llm, l -> l.ratePerMinute);
                registry.gauge("opicnic.outbound.limiter.congestion_rate", java.util.List.of(io.micrometer.core.instrument.Tag.of("kind", "stt")), stt, l -> l.congestionRatePerMinute);
                registry.gauge("opicnic.outbound.limiter.congestion_rate", java.util.List.of(io.micrometer.core.instrument.Tag.of("kind", "llm")), llm, l -> l.congestionRatePerMinute);
            }
        }
    }

    public Mode mode(ProviderKind kind) {
        return lane(kind).mode;
    }

    // 헤더 방식인 종류가 하나라도 있으면 워커가 제공자의 Retry-After를 지킨다
    public boolean usesHeaders() {
        return stt.mode == Mode.HEADER || llm.mode == Mode.HEADER;
    }

    private Lane lane(ProviderKind kind) {
        return kind == ProviderKind.STT ? stt : llm;
    }

    public <T> T call(ProviderKind kind, Supplier<T> call) {
        Lane lane = lane(kind);
        Mode mode = lane.mode;
        if (mode == Mode.OFF && !lane.congestion) return call.get();
        lane.acquire();
        HEADERS.remove();
        try {
            T result = call.get();
            if (mode == Mode.HEADER) lane.onHeaders(HEADERS.get(), null, false);
            if (lane.congestion) {
                lane.tickOk.incrementAndGet();
                lane.lastOkNanos = System.nanoTime();
            }
            return result;
        } catch (ExternalCallException e) {
            if (lane.congestion && e.reason() == Reason.UNAVAILABLE) {
                lane.tickOverload.incrementAndGet();
                if (System.nanoTime() - lane.lastOkNanos < RECENT_SUCCESS_NANOS) {
                    throw new ExternalCallException(Reason.CONGESTED, e.getMessage(), e);
                }
            }
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
            if (lane.mode == Mode.AIMD && now - lane.last429Nanos >= TimeUnit.SECONDS.toNanos(1)) {
                lane.setRate(lane.ratePerMinute + Math.max(1, lane.base * 2 / 100));
            }
            if (lane.congestion) lane.adjustCongestion(lane.tickOk.getAndSet(0), lane.tickOverload.getAndSet(0));
        }
    }

    // 테스트·진단용
    long currentRatePerMinute(ProviderKind kind) {
        return lane(kind).ratePerMinute;
    }

    long congestionRatePerMinute(ProviderKind kind) {
        return lane(kind).congestionRatePerMinute;
    }

    long effectiveRatePerMinute(ProviderKind kind) {
        return lane(kind).effective();
    }

    // 테스트용: 1초 틱을 기다리지 않고 지금까지 센 성공·5xx로 혼잡 속도를 조정한다
    void adjustCongestionNow(ProviderKind kind) {
        Lane lane = lane(kind);
        lane.adjustCongestion(lane.tickOk.getAndSet(0), lane.tickOverload.getAndSet(0));
    }

    @PreDestroy
    void shutdown() {
        if (ticker != null) ticker.shutdownNow();
    }

    private static final class Lane {
        final Mode mode;
        final long base;                 // 원래 한도(분당). 0 이하면 제한 없음
        final boolean congestion;
        final LocalBucket bucket;
        volatile long ratePerMinute;              // 한도 쪽(429·헤더) 속도
        volatile long congestionRatePerMinute;    // 혼잡(과부하 5xx) 속도. 버킷은 둘 중 작은 값
        private long appliedPerMinute;
        final java.util.concurrent.atomic.AtomicInteger tickOk = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger tickOverload = new java.util.concurrent.atomic.AtomicInteger();
        volatile long pausedUntilNanos;
        volatile long last429Nanos = Long.MIN_VALUE / 2;
        volatile long lastDecreaseNanos = Long.MIN_VALUE / 2;
        volatile long lastAdjustNanos = Long.MIN_VALUE / 2;
        volatile long lastOkNanos = Long.MIN_VALUE / 2;

        Lane(Mode mode, long base, boolean congestion) {
            this.mode = mode;
            this.base = base;
            this.congestion = congestion;
            this.ratePerMinute = base;
            this.congestionRatePerMinute = base;
            this.appliedPerMinute = base;
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

        private long clamp(long perMinute) {
            return Math.min(base, Math.max(Math.max(1, base / 10), perMinute));
        }

        void setRate(long perMinute) {
            if (bucket == null) return;
            ratePerMinute = clamp(perMinute);
            apply();
        }

        void setCongestionRate(long perMinute) {
            if (bucket == null) return;
            congestionRatePerMinute = clamp(perMinute);
            apply();
        }

        synchronized long effective() {
            return appliedPerMinute;
        }

        private synchronized void apply() {
            long next = Math.min(ratePerMinute, congestionRatePerMinute);
            if (next == appliedPerMinute) return;
            appliedPerMinute = next;
            bucket.replaceConfiguration(BucketConfiguration.builder().addLimit(limit(next)).build(), TokensInheritanceStrategy.AS_IS);
        }

        // 1초 동안의 성공·5xx로: 섞였으면 절반, 5xx 없으면 2%씩 올림, 전부 실패면 그대로(완전 장애는 서킷 몫)
        void adjustCongestion(int ok, int overload) {
            if (overload == 0) setCongestionRate(congestionRatePerMinute + Math.max(1, base * 2 / 100));
            else if (ok > 0) setCongestionRate(congestionRatePerMinute / 2);
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
