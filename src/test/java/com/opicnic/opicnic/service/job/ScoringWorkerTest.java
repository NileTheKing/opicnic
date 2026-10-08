package com.opicnic.opicnic.service.job;

import com.opicnic.opicnic.domain.job.ScoringJobItem.FailureKind;
import com.opicnic.opicnic.exception.AudioNotFoundException;
import com.opicnic.opicnic.exception.ExternalCallException;
import com.opicnic.opicnic.exception.ExternalCallException.Reason;
import com.opicnic.opicnic.exception.InvalidModelOutputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// 워커의 순수 로직만. 집기·회수·마무리 전이는 ScoringJobRepositoryTest(실제 DB), 전 구간은 scripts/s1-async.sh로 검증한다.
class ScoringWorkerTest {

    @Test
    @DisplayName("백오프: 0 ~ 천장 무작위(full jitter), 천장은 2s부터 2배(429는 4s부터) 상한 60s")
    void backoffIsFullJitterUnderDoublingCeiling() {
        assertCeiling(1, false, 2000L);
        assertCeiling(3, false, 8000L);
        assertCeiling(1, true, 4000L);
        assertCeiling(2, true, 8000L);
        // 상한: 오래 재시도해도 문항당 분당 한 번 꼴
        assertCeiling(6, false, 60_000L);
        assertCeiling(40, true, 60_000L);
    }

    // 천장을 넘지 않고, 천장 안 전체로 흩어진다 — 천장에 붙어 같은 박자로 몰리지 않게(2026-09-26 재시도 폭주 실험)
    private static void assertCeiling(int attempt, boolean rateLimited, long ceiling) {
        long min = Long.MAX_VALUE, max = 0;
        for (int i = 0; i < 500; i++) {
            long ms = ScoringWorker.backoff(attempt, rateLimited).toMillis();
            assertThat(ms).isBetween(0L, ceiling);
            min = Math.min(min, ms);
            max = Math.max(max, ms);
        }
        assertThat(min).isLessThan(ceiling / 5);
        assertThat(max).isGreaterThan(ceiling * 4 / 5);
    }

    @Test
    @DisplayName("분류: 기다리면 풀리는가 — 파일 없음·요청 거절은 영구, 모델 형식 오류는 따로, 나머지는 일시적")
    void classifyByWhetherWaitingHelps() {
        assertThat(ScoringWorker.classify(new AudioNotFoundException("k", null))).isEqualTo(FailureKind.PERMANENT);
        assertThat(ScoringWorker.classify(failure(Reason.BAD_REQUEST))).isEqualTo(FailureKind.PERMANENT);

        assertThat(ScoringWorker.classify(new InvalidModelOutputException("contentScore 누락"))).isEqualTo(FailureKind.INVALID_OUTPUT);

        assertThat(ScoringWorker.classify(failure(Reason.RATE_LIMITED))).isEqualTo(FailureKind.TRANSIENT);
        assertThat(ScoringWorker.classify(failure(Reason.UNAVAILABLE))).isEqualTo(FailureKind.TRANSIENT);
        assertThat(ScoringWorker.classify(failure(Reason.PROVIDER_ERROR))).isEqualTo(FailureKind.TRANSIENT);
        // 원인 체인 안쪽까지 본다
        assertThat(ScoringWorker.classify(new RuntimeException("wrap", failure(Reason.BAD_REQUEST)))).isEqualTo(FailureKind.PERMANENT);
    }

    @Test
    @DisplayName("분류: 우리 코드의 일반 버그(IllegalStateException)는 모델 형식 오류로 오인하지 않는다")
    void plainBugIsNotInvalidOutput() {
        assertThat(ScoringWorker.classify(new IllegalStateException("세션이 만료되었습니다."))).isEqualTo(FailureKind.TRANSIENT);
    }

    @Test
    @DisplayName("429만 rate-limit(긴 백오프)으로 본다")
    void rateLimitedOnlyFor429() {
        assertThat(ScoringWorker.isRateLimited(failure(Reason.RATE_LIMITED))).isTrue();
        assertThat(ScoringWorker.isRateLimited(new RuntimeException("wrap", failure(Reason.RATE_LIMITED)))).isTrue();
        assertThat(ScoringWorker.isRateLimited(failure(Reason.UNAVAILABLE))).isFalse();
    }

    private static ExternalCallException failure(Reason reason) {
        return new ExternalCallException(reason, reason.name(), null);
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
