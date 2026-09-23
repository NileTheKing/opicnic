package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.job.ScoringJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ScoringJobRepository extends JpaRepository<ScoringJob, String> {
    Optional<ScoringJob> findByIdAndMemberId(String id, Long memberId);

    // 홈 배너: 사용자가 아직 결과를 안 본 가장 최근 잡(채점 중이거나, 끝났는데 안 봤거나).
    // since로 최근 것만 — resultSeenAt 컬럼이 생기기 전의 잡은 전부 null이라 옛 잡이 뜨지 않게 한다
    Optional<ScoringJob> findFirstByMemberIdAndResultSeenAtIsNullAndCreatedAtAfterOrderByCreatedAtDesc(Long memberId, LocalDateTime since);

    // 문항 마무리(DONE/FAILED 확정 + attempt 닫기)는 이 락 아래서만. 마지막 문항 여러 개가 동시에 끝나면
    // 각자 "아직 남았다"를 보고 아무도 COMPLETED로 못 바꾸는 경쟁이 있다 — 잡 행 락으로 마무리를 직렬화한다.
    // MySQL RR에서도 락 획득 후의 첫 일반 SELECT가 스냅샷을 잡으므로 다른 문항의 커밋을 본다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ScoringJob j where j.id = :id")
    Optional<ScoringJob> findByIdForUpdate(@Param("id") String id);
}
