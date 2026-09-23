package com.opicnic.opicnic.service.job;

import com.opicnic.opicnic.domain.job.ScoringJobItem.FailureKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.time.Duration;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;

// 워커의 순수 로직만. 집기·회수·마무리 전이는 ScoringJobRepositoryTest(실제 DB), 전 구간은 scripts/s1-async.sh로 검증한다.
class ScoringWorkerTest {

    @Test
    @DisplayName("백오프: 2s부터 2배(429는 4s부터), 상한 60s + jitter 1s 미만")
    void backoffDoublesUpToCap() {
        assertThat(ScoringWorker.backoff(1, false).toMillis()).isBetween(2000L, 2999L);
        assertThat(ScoringWorker.backoff(2, false).toMillis()).isBetween(4000L, 4999L);
        assertThat(ScoringWorker.backoff(3, false).toMillis()).isBetween(8000L, 8999L);
        assertThat(ScoringWorker.backoff(1, true).toMillis()).isBetween(4000L, 4999L);
        assertThat(ScoringWorker.backoff(2, true).toMillis()).isBetween(8000L, 8999L);
        // 상한: 오래 재시도해도 문항당 분당 한 번 꼴
        assertThat(ScoringWorker.backoff(6, false).toMillis()).isBetween(60_000L, 60_999L);
        assertThat(ScoringWorker.backoff(40, true).toMillis()).isBetween(60_000L, 60_999L);
    }

    @Test
    @DisplayName("분류: 기다리면 풀리는가 — 파일 문제는 영구, LLM 형식 오류는 따로, 나머지는 일시적")
    void classifyByWhetherWaitingHelps() {
        assertThat(ScoringWorker.classify(NoSuchKeyException.builder().message("no key").build())).isEqualTo(FailureKind.PERMANENT);
        assertThat(ScoringWorker.classify(new NoSuchElementException("객체 없음"))).isEqualTo(FailureKind.PERMANENT);
        assertThat(ScoringWorker.classify(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "bad audio", HttpHeaders.EMPTY, null, null)))
                .isEqualTo(FailureKind.PERMANENT);

        assertThat(ScoringWorker.classify(new IllegalStateException("contentScore 누락"))).isEqualTo(FailureKind.INVALID_OUTPUT);

        assertThat(ScoringWorker.classify(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "429", HttpHeaders.EMPTY, null, null)))
                .isEqualTo(FailureKind.TRANSIENT);
        assertThat(ScoringWorker.classify(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "503", HttpHeaders.EMPTY, null, null)))
                .isEqualTo(FailureKind.TRANSIENT);
        assertThat(ScoringWorker.classify(new ResourceAccessException("Read timed out"))).isEqualTo(FailureKind.TRANSIENT);
        // 원인 체인 안쪽까지 본다 — STT 래핑 RuntimeException 안의 파일 거절
        assertThat(ScoringWorker.classify(new RuntimeException("stt", HttpClientErrorException.create(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "415", HttpHeaders.EMPTY, null, null))))
                .isEqualTo(FailureKind.PERMANENT);
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
