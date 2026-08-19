package com.opicnic.opicnic.service.attempt;

import com.opicnic.opicnic.domain.attempt.PracticeAttempt;

import java.util.Optional;

public interface PracticeAttemptStore {
    void save(PracticeAttempt attempt);
    Optional<PracticeAttempt> findById(String attemptId);
    void markSubmitted(String attemptId);

    // DATA-01: 동시 finalize 요청 중 정확히 하나만 true를 받도록 원자적으로 상태를 전이한다.
    // 이미 SUBMITTED거나 존재하지 않으면 false.
    boolean tryMarkSubmitted(String attemptId);
}
