package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.FeedbackResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FeedbackResultRepository extends JpaRepository<FeedbackResult, Long> {
    List<FeedbackResult> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    List<FeedbackResult> findByMemberIdOrderByCreatedAtDesc(Long memberId, org.springframework.data.domain.Pageable pageable);

    // PERF-01: 홈/학습분석/오늘/시험계획처럼 점수·등급·유형 요약만 필요한 화면 전용.
    // sttText/진단/quote/fix/모범답변 같은 TEXT 컬럼 15개를 아예 SELECT하지 않는다.
    @Query("""
            select new com.opicnic.opicnic.domain.FeedbackResult(
                r.id, r.questionType, r.surveyTopicName, r.comboCategory, r.overallGrade,
                r.mainPointScore, r.expressionScore, r.accuracyScore, r.contentScore, r.fluencyScore,
                r.createdAt)
            from FeedbackResult r
            where r.member.id = :memberId
            order by r.createdAt desc
            """)
    List<FeedbackResult> findSummaryByMemberId(@Param("memberId") Long memberId);
    long countByMemberId(Long memberId);
    List<FeedbackResult> findByMemberIdAndCreatedAtAfter(Long memberId, LocalDateTime since);
    Optional<FeedbackResult> findByIdAndMemberId(Long id, Long memberId);

    // 비동기 모의고사 결과 화면(ScoringJobViewController): attemptId = ScoringJob.id
    List<FeedbackResult> findAllByAttemptId(String attemptId);

    // 게스트 예시 복사: @CreationTimestamp가 insert 시각으로 덮어쓰므로 저장 후 시각을 따로 맞춘다
    @Modifying
    @Query("update FeedbackResult r set r.createdAt = :createdAt where r.id = :id")
    int updateCreatedAt(@Param("id") Long id, @Param("createdAt") LocalDateTime createdAt);

    // 게스트 정리
    @Modifying
    @Query("delete from FeedbackResult r where r.member.id = :memberId")
    int deleteByMemberId(@Param("memberId") Long memberId);
}
