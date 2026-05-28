package com.opicnic.opicnic.service.attempt;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.AttemptStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class CaffeinePracticeAttemptStore implements PracticeAttemptStore {

    private final Cache<String, PracticeAttempt> cache = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.HOURS)
            .maximumSize(10_000)
            .build();

    @Override
    public void save(PracticeAttempt attempt) {
        cache.put(attempt.attemptId(), attempt);
    }

    @Override
    public Optional<PracticeAttempt> findById(String attemptId) {
        return Optional.ofNullable(cache.getIfPresent(attemptId));
    }

    @Override
    public void markSubmitted(String attemptId) {
        findById(attemptId).ifPresent(a ->
                cache.put(attemptId, a.withStatus(AttemptStatus.SUBMITTED)));
    }
}
