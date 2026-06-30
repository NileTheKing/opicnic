package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.FeedbackResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackResultRepository extends JpaRepository<FeedbackResult, Long> {
    List<FeedbackResult> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    List<FeedbackResult> findByMemberIdOrderByCreatedAtDesc(Long memberId, org.springframework.data.domain.Pageable pageable);
    long countByMemberId(Long memberId);
}
