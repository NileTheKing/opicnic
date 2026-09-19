package com.opicnic.opicnic.service.job;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// 워커의 순수 로직만. 집기·회수·마무리 전이는 ScoringJobRepositoryTest(실제 DB), 전 구간은 scripts/s1-async.sh로 검증한다.
class ScoringWorkerTest {

    @Test
    @DisplayName("백오프: 동기 경로와 같은 값 — 429는 3s·6s + jitter, 그 외 1s·2s + jitter")
    void backoffMatchesSyncPath() {
        assertThat(ScoringWorker.backoff(1, true).toMillis()).isBetween(3000L, 3999L);
        assertThat(ScoringWorker.backoff(2, true).toMillis()).isBetween(6000L, 6999L);
        assertThat(ScoringWorker.backoff(1, false).toMillis()).isBetween(1000L, 1299L);
        assertThat(ScoringWorker.backoff(2, false).toMillis()).isBetween(2000L, 2299L);
    }

    @Test
    @DisplayName("서킷: 창이 다 차기 전엔 안 열리고, 찬 뒤 실패 비율이 임계 이상이면 열린다")
    void circuitOpensOnlyWhenWindowFullAndRatioExceeded() {
        var circuit = new ScoringWorker.FailureCircuit(10, 0.8, Duration.ofSeconds(30));

        for (int i = 0; i < 9; i++) circuit.record(false);   // 9건 실패지만 창(10) 미달
        assertThat(circuit.isOpen()).isFalse();

        circuit.record(false);                                 // 10/10 실패
        assertThat(circuit.isOpen()).isTrue();
    }

    @Test
    @DisplayName("서킷: 성공이 섞여 임계 미만이면 열리지 않는다 (10건 중 실패 7건 < 80%)")
    void circuitStaysClosedBelowRatio() {
        var circuit = new ScoringWorker.FailureCircuit(10, 0.8, Duration.ofSeconds(30));
        for (int i = 0; i < 7; i++) circuit.record(false);
        for (int i = 0; i < 3; i++) circuit.record(true);
        assertThat(circuit.isOpen()).isFalse();
    }

    @Test
    @DisplayName("서킷: 열린 시간이 지나면 닫힌다 (반열림 — 다시 집어본다)")
    void circuitClosesAfterOpenDuration() throws InterruptedException {
        var circuit = new ScoringWorker.FailureCircuit(2, 1.0, Duration.ofMillis(100));
        circuit.record(false); circuit.record(false);
        assertThat(circuit.isOpen()).isTrue();
        Thread.sleep(150);
        assertThat(circuit.isOpen()).isFalse();
    }
}
