package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.FeedbackResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackResultRepository extends JpaRepository<FeedbackResult, Long> {
    List<FeedbackResult> findByMemberIdOrderByCreatedAtDesc(Long memberId);
}
