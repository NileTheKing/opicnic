package com.opicnic.opicnic.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.nio.charset.StandardCharsets;

// mock 경로(STT·LLM enabled=false) 전용 — 부하에 반응하는 가짜 제공자. 확률 주입(STT_MOCK_429_RATE 등)은 몇 건을 부르든
// 실패율이 같아서 "재시도가 몰리면 더 실패한다"(재시도 폭주)가 안 생긴다. 거절은 처리 지연 없이 바로 돌아온다(실제 제공자처럼).
// 한도는 두 가지가 따로 있고 둘 다 기본 꺼짐 — 켜지 않으면 기존 mock과 동일. dev 스위치(DevPracticeController /mock-provider)로 실행 중에 바꾼다.
//  1) limitPerSecond: 제공자 하나(Groq)가 STT·채점·태깅을 합쳐 초당 그만큼만 받는다(고정 1초 창). 넘으면 즉시 429. down이면 즉시 503.
//  2) 종류별 분당 한도(sttPerMinute, llmPerMinute): 토큰 버킷. 1초마다 한도/60씩 채우고 최대 버스트는 한도/60 x 5.
//     capacity(0~1)는 한도에 곱하는 용량 비율 — 0.4면 제공자가 갑자기 용량의 40%만 낸다. 호출하는 쪽은 원래 한도만 안다.
//     countRejected=true면 토큰이 없어 429로 거절할 때도 토큰 1개를 쓴다(실제 OpenAI는 실패한 요청도 분당 한도에 센다고 문서에 적음).
//     잔고가 음수가 되고 하한은 -(현재 용량 기준 분당 한도) 즉 최대 1분치 빚. 채우는 속도와 버스트 상한은 그대로. 기본 false.
//     headers=true면 응답마다 x-ratelimit-remaining-requests(남은 토큰)와 x-ratelimit-reset-requests(버킷이 다 찰 때까지 초),
//     429에는 Retry-After(토큰 1개가 생길 때까지 초)를 준다. OpenAI·Groq가 응답마다 한도 헤더를 주는 것을 흉내낸다.
//     overload=true면 토큰이 없을 때 429 대신 503 "model is overloaded"로 거절한다 — 제공자 사정으로 받아주는 양이 준 것은
//     실제로 이렇게 온다(Gemini API 503, OpenAI 503 server_is_overloaded). 과부하 거절은 내 한도와 무관하니 countRejected와 상관없이
//     토큰을 쓰지 않고 한도 헤더도 주지 않는다. 기본 false.
//  3) 종류별 분당 토큰 한도(sttTokensPerMinute, llmTokensPerMinute, 0 = 끔). 실제 제공자는 요청 수와 토큰 중 먼저 차는 쪽에서 429를 준다.
//     호출마다 CallTokens의 값만큼 쓴다(OpenAI 계산 규칙: max(max_tokens, 입력 추정)). 채우기·버스트·용량 비율은 요청 수 버킷과 같다.
//     거절된 호출은 토큰을 쓰지 않는다(요청 수는 countRejected를 따른다) — 거절이 토큰 한도를 깎는다는 문서가 없다.
//     overloadTimeoutMs(종류별, 0 = 끔)를 주면 과부하 거절을 503 대신 그 시간만큼 기다린 뒤 읽기 타임아웃으로 낸다 — 과부하가 응답 지연으로
//     나타나 우리 read-timeout에 걸리는 경우. 기다리는 동안 호출한 쪽(워커 슬롯)이 묶인다.
public final class MockProvider {

    private static final String HDR_REMAINING = "x-ratelimit-remaining-requests";
    private static final String HDR_RESET = "x-ratelimit-reset-requests";

    private static volatile boolean down;
    private static volatile int limitPerSecond;   // 0 = 무제한
    private static volatile boolean headers;
    private static volatile boolean countRejected;
    private static volatile boolean overload;
    private static volatile long sttOverloadTimeoutMs;
    private static volatile long llmOverloadTimeoutMs;
    private static long windowSecond;
    private static int windowCount;
    private static final Lane STT = new Lane();
    private static final Lane LLM = new Lane();

    private MockProvider() {}

    public static void configure(boolean down, int limitPerSecond) {
        MockProvider.down = down;
        MockProvider.limitPerSecond = limitPerSecond;
    }

    // perMinute 0 = 종류별 한도 없음. capacity는 한도에 곱하는 비율(1 = 원래)
    public static void configureLimits(int sttPerMinute, int llmPerMinute, double capacity, boolean headers) {
        MockProvider.headers = headers;
        STT.configure(sttPerMinute, capacity);
        LLM.configure(llmPerMinute, capacity);
    }

    public static void configureTokenLimits(int sttTokensPerMinute, int llmTokensPerMinute, double capacity) {
        STT.configureTokens(sttTokensPerMinute, capacity);
        LLM.configureTokens(llmTokensPerMinute, capacity);
    }

    public static void setCountRejected(boolean countRejected) {
        MockProvider.countRejected = countRejected;
        STT.clampDebt();
        LLM.clampDebt();
    }

    public static void setOverload(boolean overload) {
        setOverload(overload, 0, 0);
    }

    public static void setOverload(boolean overload, long sttTimeoutMs, long llmTimeoutMs) {
        MockProvider.overload = overload;
        MockProvider.sttOverloadTimeoutMs = Math.max(0, sttTimeoutMs);
        MockProvider.llmOverloadTimeoutMs = Math.max(0, llmTimeoutMs);
    }

    // 테스트용: 종류별 남은 토큰(토큰 한도)
    static double tokenLeftForTest(ProviderKind kind) {
        return (kind == ProviderKind.STT ? STT : LLM).tokenLeftForTest();
    }

    // 테스트용: 종류별 현재 토큰 잔고(음수 = 거절 빚)
    static double tokensForTest(ProviderKind kind) {
        return (kind == ProviderKind.STT ? STT : LLM).tokens();
    }

    static void admit(ProviderKind kind) {
        admit(kind, 0);
    }

    static void admit(ProviderKind kind, int callTokens) {
        if (down) {
            throw HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                    HttpHeaders.EMPTY, "Service Unavailable".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        }
        if (limitPerSecond > 0 && !takeSlot()) {
            throw HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                    HttpHeaders.EMPTY, "Rate limit reached (mock provider)".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        }
        Lane lane = kind == ProviderKind.STT ? STT : LLM;
        Lane.Result r = lane.take(callTokens);
        if (r == null) return;   // 이 종류는 한도 없음
        if (!r.admitted && overload) {
            long wait = kind == ProviderKind.STT ? sttOverloadTimeoutMs : llmOverloadTimeoutMs;
            if (wait > 0) {
                try { Thread.sleep(wait); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                throw new org.springframework.web.client.ResourceAccessException("I/O error (mock provider overloaded): Read timed out",
                        new java.net.SocketTimeoutException("Read timed out"));
            }
            throw HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", HttpHeaders.EMPTY,
                    "{\"error\":{\"code\":503,\"message\":\"The model is overloaded. Please try again later.\",\"status\":\"UNAVAILABLE\"}}"
                            .getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        }
        if (headers) OutboundLimiter.reportHeaders(r.remaining, r.resetSeconds);
        if (!r.admitted) {
            HttpHeaders h = new HttpHeaders();
            if (headers) {
                h.set(HDR_REMAINING, String.valueOf(r.remaining));
                h.set(HDR_RESET, String.format("%.1f", r.resetSeconds));
                h.set(HttpHeaders.RETRY_AFTER, String.valueOf(r.retryAfterSeconds));
            }
            throw HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                    h, "Rate limit reached (mock provider)".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        }
    }

    // 고정 1초 창. 받아들인 호출만 센다(거절된 호출은 한도를 쓰지 않음)
    private static synchronized boolean takeSlot() {
        long now = System.currentTimeMillis() / 1000;
        if (now != windowSecond) {
            windowSecond = now;
            windowCount = 0;
        }
        if (windowCount >= limitPerSecond) return false;
        windowCount++;
        return true;
    }

    // 종류별 토큰 버킷. 1초 단위로 채운다(한도/60씩), 최대 버스트 = 한도/60 x 5. 거절된 호출은 토큰을 쓰지 않는다
    private static final class Lane {
        private int basePerMinute;
        private double ratePerSecond;   // 용량 비율을 곱한 값. 0 = 한도 없음
        private double max;
        private double tokens;
        private long lastRefillMs;
        private int tokensPerMinute;          // 토큰 한도. 0 = 없음
        private double tokenRatePerSecond, tokenMax, tokenLeft;

        record Result(boolean admitted, int remaining, double resetSeconds, long retryAfterSeconds) {}

        synchronized void configure(int perMinute, double capacity) {
            double rate = perMinute <= 0 ? 0 : perMinute * Math.max(0, capacity) / 60.0;
            double newMax = rate * 5;
            long now = System.currentTimeMillis();
            // 한도 자체가 바뀌면 가득 찬 상태로 시작, 용량 비율만 바뀌면 지금 토큰을 새 상한으로 자른다
            tokens = (perMinute != basePerMinute || max == 0) ? newMax : Math.min(tokens, newMax);
            tokens = Math.max(tokens, -rate * 60);
            basePerMinute = perMinute;
            ratePerSecond = rate;
            max = newMax;
            lastRefillMs = now;
        }

        synchronized void configureTokens(int perMinute, double capacity) {
            double rate = perMinute <= 0 ? 0 : perMinute * Math.max(0, capacity) / 60.0;
            double newMax = rate * 5;
            tokenLeft = (perMinute != tokensPerMinute || tokenMax == 0) ? newMax : Math.min(tokenLeft, newMax);
            tokensPerMinute = perMinute;
            tokenRatePerSecond = rate;
            tokenMax = newMax;
        }

        synchronized double tokens() { return tokens; }

        synchronized double tokenLeftForTest() { return tokenLeft; }

        synchronized void clampDebt() { tokens = Math.max(tokens, -ratePerSecond * 60); }

        synchronized Result take(int callTokens) {
            if (basePerMinute <= 0 && tokensPerMinute <= 0) return null;
            long now = System.currentTimeMillis();
            long secs = (now - lastRefillMs) / 1000;
            if (secs > 0) {
                tokens = Math.min(max, tokens + secs * ratePerSecond);
                tokenLeft = Math.min(tokenMax, tokenLeft + secs * tokenRatePerSecond);
                lastRefillMs += secs * 1000;
            }
            boolean reqOk = basePerMinute <= 0 || tokens >= 1;
            boolean tokOk = tokensPerMinute <= 0 || tokenLeft >= callTokens;
            boolean ok = reqOk && tokOk;
            if (ok) {
                if (basePerMinute > 0) tokens -= 1;
                if (tokensPerMinute > 0) tokenLeft -= callTokens;
            }
            else if (countRejected && !overload && basePerMinute > 0) tokens = Math.max(tokens - 1, -ratePerSecond * 60);   // 거절도 한도에 침, 하한 = 1분치 빚
            double reset = ratePerSecond > 0 ? (max - tokens) / ratePerSecond : 0;
            long retryAfter = ok ? 0 : (ratePerSecond > 0 ? Math.max(1, (long) Math.ceil((1 - tokens) / ratePerSecond)) : 60);
            return new Result(ok, (int) Math.max(0, Math.floor(tokens)), reset, retryAfter);
        }
    }
}
