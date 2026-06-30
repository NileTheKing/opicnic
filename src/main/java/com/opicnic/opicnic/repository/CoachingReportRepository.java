package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.CoachingReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CoachingReportRepository extends JpaRepository<CoachingReport, Long> {
    Optional<CoachingReport> findTopByMemberIdOrderByCreatedAtDesc(Long memberId);
    List<CoachingReport> findByMemberIdOrderByCreatedAtDesc(Long memberId);
}
