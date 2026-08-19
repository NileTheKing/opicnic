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
    public void markSubmitted(String attemptId) {
        findById(attemptId).ifPresent(a ->
                cache.put(attemptId, a.withStatus(AttemptStatus.SUBMITTED)));
    }

    // DATA-01: 같은 attempt에 대한 동시 finalize 요청이 둘 다 "아직 제출 안 됨"을 확인하고
    // 통과해버리는 걸 막는다. Caffeine의 ConcurrentMap 뷰(asMap())의 computeIfPresent는
    // 같은 key에 대해 원자적으로 실행되므로(다른 스레드가 그 사이에 끼어들 수 없음), 여기서
    // "이미 제출됐는지 확인"과 "제출됨으로 표시"가 한 동작으로 묶인다. 단일 인스턴스 전제 —
    // 서버가 여러 대가 되면 이 in-memory 캐시 자체를 공유 저장소(DB/Redis)로 옮겨야 한다.
    @Override
    public boolean tryMarkSubmitted(String attemptId) {
        AtomicBoolean transitioned = new AtomicBoolean(false);
        cache.asMap().computeIfPresent(attemptId, (id, existing) -> {
            if (existing.status() == AttemptStatus.SUBMITTED) {
                return existing;
            }
            transitioned.set(true);
            return existing.withStatus(AttemptStatus.SUBMITTED);
        });
        return transitioned.get();
    }
}
