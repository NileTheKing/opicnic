package com.opicnic.opicnic.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.nio.charset.StandardCharsets;

// mock 경로(STT·LLM enabled=false) 전용 — 부하에 반응하는 가짜 제공자. 확률 주입(STT_MOCK_429_RATE 등)은 몇 건을 부르든
// 실패율이 같아서 "재시도가 몰리면 더 실패한다"(재시도 폭주)가 안 생긴다. 여기선 제공자 하나(Groq)가 STT·채점·태깅을 합쳐
// 초당 limitPerSecond건만 받고 넘으면 즉시 429, down이면 즉시 503. 거절은 처리 지연 없이 바로 돌아온다(실제 제공자처럼).
// 둘 다 기본 꺼짐 — 켜지 않으면 기존 mock과 동일. dev 스위치(DevPracticeController /mock-provider)로 실행 중에 바꾼다.
public final class MockProvider {

    private static volatile boolean down;
    private static volatile int limitPerSecond;   // 0 = 무제한
    private static long windowSecond;
    private static int windowCount;

    private MockProvider() {}

    public static void configure(boolean down, int limitPerSecond) {
        MockProvider.down = down;
        MockProvider.limitPerSecond = limitPerSecond;
    }

    static void admit() {
        if (down) {
            throw HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                    HttpHeaders.EMPTY, "Service Unavailable".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        }
        if (limitPerSecond > 0 && !takeSlot()) {
            throw HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                    HttpHeaders.EMPTY, "Rate limit reached (mock provider)".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
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
}
