package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.CoachingReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;

public interface CoachingReportRepository extends JpaRepository<CoachingReport, Long> {
    Optional<CoachingReport> findTopByMemberIdOrderByCreatedAtDesc(Long memberId);
    List<CoachingReport> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    Optional<CoachingReport> findByIdAndMemberId(Long id, Long memberId);

    // 게스트 예시 복사 / 정리 (FeedbackResultRepository.updateCreatedAt과 같은 이유)
    @Modifying
    @Query("update CoachingReport c set c.createdAt = :createdAt where c.id = :id")
    int updateCreatedAt(@Param("id") Long id, @Param("createdAt") LocalDateTime createdAt);

    @Modifying
    @Query("delete from CoachingReport c where c.member.id = :memberId")
    int deleteByMemberId(@Param("memberId") Long memberId);
}
