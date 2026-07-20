package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.FeedbackResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FeedbackResultRepository extends JpaRepository<FeedbackResult, Long> {
    List<FeedbackResult> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    List<FeedbackResult> findByMemberIdOrderByCreatedAtDesc(Long memberId, org.springframework.data.domain.Pageable pageable);
    long countByMemberId(Long memberId);
    List<FeedbackResult> findByMemberIdAndCreatedAtAfter(Long memberId, LocalDateTime since);
    Optional<FeedbackResult> findByIdAndMemberId(Long id, Long memberId);
}
