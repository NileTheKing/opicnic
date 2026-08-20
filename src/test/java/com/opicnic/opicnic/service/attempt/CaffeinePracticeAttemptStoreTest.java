package com.opicnic.opicnic.service.attempt;

import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.AttemptStatus;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// DATA-01/REVIEW-01 회귀 테스트: 같은 attempt에 대한 동시 finalize 요청이 둘 다 "아직 제출 안 됨"을
// 통과해버려 피드백이 중복 저장되던 문제. mock으로 순서를 흉내내는 것만으로는 진짜 스레드 경쟁을
// 증명하지 못하므로, 실제 여러 스레드가 동시에 tryStartFinalizing을 두드리는 상황을 재현한다.
class CaffeinePracticeAttemptStoreTest {

    private PracticeAttempt inProgressAttempt(String attemptId) {
        return new PracticeAttempt(attemptId, List.of(1L, 2L, 3L), 1L, PracticeMode.COMBO,
                null, null, Instant.now().plusSeconds(3600), AttemptStatus.IN_PROGRESS);
    }

    @Test
    void onlyOneConcurrentCallerWinsTheTransitionToFinalizing() throws InterruptedException {
        CaffeinePracticeAttemptStore store = new CaffeinePracticeAttemptStore();
        String attemptId = "attempt-1";
        store.save(inProgressAttempt(attemptId));

        int threadCount = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (store.tryStartFinalizing(attemptId)) {
                    successCount.incrementAndGet();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown(); // 50개 스레드를 동시에 풀어서 진짜 경쟁 상황을 만든다
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        // 50개가 동시에 두드려도 정확히 1개만 성공해야 한다 -- 그게 아니면 피드백이 여러 번 저장된다.
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(store.findById(attemptId).orElseThrow().status()).isEqualTo(AttemptStatus.FINALIZING);
    }

    @Test
    void secondCallAfterSuccessfulTransitionReturnsFalse() {
        CaffeinePracticeAttemptStore store = new CaffeinePracticeAttemptStore();
        String attemptId = "attempt-1";
        store.save(inProgressAttempt(attemptId));

        assertThat(store.tryStartFinalizing(attemptId)).isTrue();
        assertThat(store.tryStartFinalizing(attemptId)).isFalse();
    }

    @Test
    void unknownAttemptReturnsFalseForEveryTransition() {
        CaffeinePracticeAttemptStore store = new CaffeinePracticeAttemptStore();
        assertThat(store.tryStartFinalizing("no-such-attempt")).isFalse();
        assertThat(store.confirmSubmitted("no-such-attempt")).isFalse();
        assertThat(store.revertToInProgress("no-such-attempt")).isFalse();
    }

    // REVIEW-01 회귀 테스트: DB 저장 실패 시 FINALIZING -> IN_PROGRESS로 되돌려 재시도할 수 있어야 한다.
    @Test
    void revertToInProgressAllowsRetryAfterFailure() {
        CaffeinePracticeAttemptStore store = new CaffeinePracticeAttemptStore();
        String attemptId = "attempt-1";
        store.save(inProgressAttempt(attemptId));

        assertThat(store.tryStartFinalizing(attemptId)).isTrue();
        assertThat(store.findById(attemptId).orElseThrow().status()).isEqualTo(AttemptStatus.FINALIZING);

        // DB 저장 실패를 흉내낸다 -> 복구
        assertThat(store.revertToInProgress(attemptId)).isTrue();
        assertThat(store.findById(attemptId).orElseThrow().status()).isEqualTo(AttemptStatus.IN_PROGRESS);

        // 복구됐으니 재시도(다시 FINALIZING 진입)가 가능해야 한다.
        assertThat(store.tryStartFinalizing(attemptId)).isTrue();
        assertThat(store.confirmSubmitted(attemptId)).isTrue();
        assertThat(store.findById(attemptId).orElseThrow().status()).isEqualTo(AttemptStatus.SUBMITTED);
    }

    @Test
    void confirmSubmittedFailsWhenNotCurrentlyFinalizing() {
        CaffeinePracticeAttemptStore store = new CaffeinePracticeAttemptStore();
        String attemptId = "attempt-1";
        store.save(inProgressAttempt(attemptId));

        // FINALIZING을 거치지 않고 바로 SUBMITTED로 확정하려는 시도는 실패해야 한다.
        assertThat(store.confirmSubmitted(attemptId)).isFalse();
        assertThat(store.findById(attemptId).orElseThrow().status()).isEqualTo(AttemptStatus.IN_PROGRESS);
    }
}
