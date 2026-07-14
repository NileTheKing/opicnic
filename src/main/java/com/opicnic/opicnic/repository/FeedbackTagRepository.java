package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.FeedbackTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackTagRepository extends JpaRepository<FeedbackTag, Long> {
    List<FeedbackTag> findByFeedbackResultIdIn(List<Long> feedbackResultIds);
}
