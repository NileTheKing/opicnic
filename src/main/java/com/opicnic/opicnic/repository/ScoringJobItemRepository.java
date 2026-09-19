package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.job.ScoringJobItem;
import com.opicnic.opicnic.domain.job.ScoringJobItemStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface ScoringJobItemRepository extends JpaRepository<ScoringJobItem, Long> {

    // 워커가 집을 후보: QUEUED이고 백오프 시각이 지났거나 없는 것. 오래 기다린 순
    @Query("select i from ScoringJobItem i where i.status = :queued " +
           "and (i.nextAttemptAt is null or i.nextAttemptAt <= :now) order by i.updatedAt asc")
    List<ScoringJobItem> findClaimable(@Param("queued") ScoringJobItemStatus queued,
                                       @Param("now") LocalDateTime now, Pageable pageable);

    default List<ScoringJobItem> findClaimable(int limit) {
        return findClaimable(ScoringJobItemStatus.QUEUED, LocalDateTime.now(), Pageable.ofSize(limit));
    }

    // 워커가 문항을 "집는" 원자적 전이. 단일 인스턴스라도 폴링 주기가 겹치면 같은 문항을 두 번 집을 수
    // 있으므로 UPDATE ... WHERE status=QUEUED 로 정확히 한 번만 성공하게 한다. 다중 인스턴스로 가면
    // SELECT ... FOR UPDATE SKIP LOCKED 로 바꾸는 자리 (ADR-0001 6절).
    // attempts도 여기서 올린다 — 집은 횟수 = 시도 횟수. 소진 판정(MAX_ATTEMPTS)은 markFailed가 한다.
    @Transactional   // 워커 폴링 루프엔 트랜잭션이 없다 — UPDATE 쿼리는 자기 트랜잭션을 가져야 한다
    @Modifying(clearAutomatically = true)
    @Query("update ScoringJobItem i set i.status = :processing, i.attempts = i.attempts + 1, i.updatedAt = :now " +
           "where i.id = :id and i.status = :queued")
    int claim(@Param("id") Long id,
              @Param("queued") ScoringJobItemStatus queued,
              @Param("processing") ScoringJobItemStatus processing,
              @Param("now") LocalDateTime now);

    default int claim(Long id) {
        return claim(id, ScoringJobItemStatus.QUEUED, ScoringJobItemStatus.PROCESSING, LocalDateTime.now());
    }

    // 워커가 죽어 PROCESSING에 멈춘 문항 회수. 재시작 직후와 주기적으로 부른다.
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update ScoringJobItem i set i.status = :queued " +
           "where i.status = :processing and i.updatedAt < :staleBefore")
    int requeueStale(@Param("staleBefore") LocalDateTime staleBefore,
                     @Param("queued") ScoringJobItemStatus queued,
                     @Param("processing") ScoringJobItemStatus processing);

    default int requeueStale(LocalDateTime staleBefore) {
        return requeueStale(staleBefore, ScoringJobItemStatus.QUEUED, ScoringJobItemStatus.PROCESSING);
    }
}
