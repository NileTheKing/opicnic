package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.FeedbackTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FeedbackTagRepository extends JpaRepository<FeedbackTag, Long> {
    List<FeedbackTag> findByFeedbackResultIdIn(List<Long> feedbackResultIds);

    // 게스트 정리: 결과 행 삭제 전에 태그를 먼저 지운다(FK)
    @Modifying
    @Query("delete from FeedbackTag t where t.feedbackResult.id in " +
           "(select r.id from FeedbackResult r where r.member.id = :memberId)")
    int deleteByMemberId(@Param("memberId") Long memberId);
}
