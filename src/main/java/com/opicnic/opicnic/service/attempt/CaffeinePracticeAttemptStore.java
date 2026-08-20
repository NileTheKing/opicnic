package com.opicnic.opicnic.service.attempt;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.AttemptStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    public boolean tryStartFinalizing(String attemptId) {
        return transition(attemptId, AttemptStatus.IN_PROGRESS, AttemptStatus.FINALIZING);
    }

    @Override
    public boolean confirmSubmitted(String attemptId) {
        return transition(attemptId, AttemptStatus.FINALIZING, AttemptStatus.SUBMITTED);
    }

    @Override
    public boolean revertToInProgress(String attemptId) {
        return transition(attemptId, AttemptStatus.FINALIZING, AttemptStatus.IN_PROGRESS);
    }

    // DATA-01/REVIEW-01: Caffeine의 ConcurrentMap 뷰(asMap())의 computeIfPresent는 같은 key에 대해
    // 원자적으로 실행되므로(다른 스레드가 그 사이에 끼어들 수 없음), "현재 상태 확인"과 "다음 상태로
    // 변경"이 한 동작으로 묶인다. 단일 인스턴스 전제 — 서버가 여러 대가 되면 이 in-memory 캐시 자체를
    // 공유 저장소(DB/Redis)로 옮겨야 한다.
    private boolean transition(String attemptId, AttemptStatus from, AttemptStatus to) {
        AtomicBoolean transitioned = new AtomicBoolean(false);
        cache.asMap().computeIfPresent(attemptId, (id, existing) -> {
            if (existing.status() != from) {
                return existing;
            }
            transitioned.set(true);
            return existing.withStatus(to);
        });
        return transitioned.get();
    }
}
