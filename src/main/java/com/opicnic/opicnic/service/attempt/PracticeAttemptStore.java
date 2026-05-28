package com.opicnic.opicnic.service.attempt;

import com.opicnic.opicnic.domain.attempt.PracticeAttempt;

import java.util.Optional;

public interface PracticeAttemptStore {
    void save(PracticeAttempt attempt);
    Optional<PracticeAttempt> findById(String attemptId);
    void markSubmitted(String attemptId);
}
