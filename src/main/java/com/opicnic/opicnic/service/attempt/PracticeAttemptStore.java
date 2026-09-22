package com.opicnic.opicnic.service.attempt;

import com.opicnic.opicnic.domain.attempt.PracticeAttempt;

import java.util.Optional;

public interface PracticeAttemptStore {
    void save(PracticeAttempt attempt);
    Optional<PracticeAttempt> findById(String attemptId);

    // REVIEW-01: 접수(submit)를 IN_PROGRESS -> FINALIZING -> SUBMITTED 3단계로 나눈다.
    // 각 전이는 원자적이어야 동시 요청 차단(정확히 하나만 IN_PROGRESS->FINALIZING 성공)이 성립한다.
    // revertToInProgress는 잡 생성 트랜잭션이 실패했을 때의 복구 경로로 남겨둔다.
    boolean tryStartFinalizing(String attemptId);
    boolean confirmSubmitted(String attemptId);
    boolean revertToInProgress(String attemptId);
}
