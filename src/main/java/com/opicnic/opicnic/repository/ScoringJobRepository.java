package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.job.ScoringJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScoringJobRepository extends JpaRepository<ScoringJob, String> {
    Optional<ScoringJob> findByIdAndMemberId(String id, Long memberId);
}
