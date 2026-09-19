package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.job.ScoringJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ScoringJobRepository extends JpaRepository<ScoringJob, String> {
    Optional<ScoringJob> findByIdAndMemberId(String id, Long memberId);

    // 문항 마무리(DONE/FAILED 확정 + attempt 닫기)는 이 락 아래서만. 마지막 문항 여러 개가 동시에 끝나면
    // 각자 "아직 남았다"를 보고 아무도 COMPLETED로 못 바꾸는 경쟁이 있다 — 잡 행 락으로 마무리를 직렬화한다.
    // MySQL RR에서도 락 획득 후의 첫 일반 SELECT가 스냅샷을 잡으므로 다른 문항의 커밋을 본다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ScoringJob j where j.id = :id")
    Optional<ScoringJob> findByIdForUpdate(@Param("id") String id);
}
