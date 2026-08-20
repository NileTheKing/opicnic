package com.opicnic.opicnic.service.attempt;

import com.opicnic.opicnic.domain.attempt.PracticeAttempt;

import java.util.Optional;

public interface PracticeAttemptStore {
    void save(PracticeAttempt attempt);
    Optional<PracticeAttempt> findById(String attemptId);

    // REVIEW-01: finalize를 IN_PROGRESS -> FINALIZING -> SUBMITTED 3단계로 나눈다.
    // 각 전이는 원자적이어야 동시 요청 차단(정확히 하나만 IN_PROGRESS->FINALIZING 성공)과
    // 실패 복구(FINALIZING->IN_PROGRESS로 되돌려 재시도 가능)가 성립한다.
    boolean tryStartFinalizing(String attemptId);
    boolean confirmSubmitted(String attemptId);
    boolean revertToInProgress(String attemptId);
}
