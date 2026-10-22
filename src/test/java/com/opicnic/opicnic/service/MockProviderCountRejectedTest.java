package com.opicnic.opicnic.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;

import static org.assertj.core.api.Assertions.assertThat;

// 가짜 제공자 count-rejected: 거절한 요청도 토큰 1개를 쓰는지(기본 false는 지금 동작 그대로)
class MockProviderCountRejectedTest {

    @AfterEach
    void reset() {
        MockProvider.setCountRejected(false);
        MockProvider.configureLimits(0, 0, 1.0, false);
    }

    private static int flood(int calls) {
        int rejected = 0;
        for (int i = 0; i < calls; i++) {
            try {
                MockProvider.admit(ProviderKind.STT);
            } catch (HttpClientErrorException e) {
                rejected++;
            }
        }
        return rejected;
    }

    @Test
    @DisplayName("기본값(false)은 거절해도 잔고가 줄지 않는다 - 잔고는 0 아래로 가지 않는다")
    void defaultDoesNotCountRejected() {
        MockProvider.setCountRejected(false);
        MockProvider.configureLimits(600, 600, 1.0, false);   // 초당 10개, 버스트 50
        assertThat(flood(500)).isGreaterThan(400);
        assertThat(MockProvider.tokensForTest(ProviderKind.STT)).isBetween(0.0, 20.0);
    }

    @Test
    @DisplayName("true면 거절이 이어질 때 잔고가 음수로 가고 -(분당 한도)에서 멈춘다")
    void countRejectedGoesNegativeAndStopsAtFloor() {
        MockProvider.setCountRejected(true);
        MockProvider.configureLimits(600, 600, 1.0, false);   // 하한 -600
        flood(300);
        assertThat(MockProvider.tokensForTest(ProviderKind.STT)).isLessThan(-200.0);
        flood(2000);
        assertThat(MockProvider.tokensForTest(ProviderKind.STT)).isBetween(-601.0, -585.0);   // 하한(채우기 1초분 오차)
    }

    @Test
    @DisplayName("하한은 현재 용량 기준이다 - 용량 40%면 -(분당 한도 x 0.4)")
    void floorFollowsCapacity() {
        MockProvider.setCountRejected(true);
        MockProvider.configureLimits(600, 600, 0.4, false);   // 하한 -240
        flood(1000);
        assertThat(MockProvider.tokensForTest(ProviderKind.STT)).isBetween(-241.0, -232.0);
    }

    @Test
    @DisplayName("보내기를 멈추면 채우는 속도만큼 잔고가 돌아온다")
    void recoversWhenSendingStops() throws InterruptedException {
        MockProvider.setCountRejected(true);
        MockProvider.configureLimits(600, 600, 1.0, false);   // 초당 10개
        flood(1000);
        double before = MockProvider.tokensForTest(ProviderKind.STT);
        Thread.sleep(2100);
        flood(1);   // 채우기는 호출 때 반영된다(이 호출은 거절로 1개 더 씀)
        assertThat(MockProvider.tokensForTest(ProviderKind.STT)).isGreaterThan(before + 15);
    }
}
