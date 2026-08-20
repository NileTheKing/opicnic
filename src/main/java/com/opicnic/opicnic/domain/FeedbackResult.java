package com.opicnic.opicnic.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private Long questionId;

    private String attemptId;

    @Enumerated(EnumType.STRING)
    private com.opicnic.opicnic.domain.enums.QuestionType questionType;

    private String surveyTopicName;

    private String comboPatternKey;

    private String comboCategory;

    private String questionContent;

    @Column(columnDefinition = "TEXT")
    private String sttText;

    @Column(columnDefinition = "TEXT")
    private String expression;

    @Column(columnDefinition = "TEXT")
    private String accuracy;

    @Column(columnDefinition = "TEXT")
    private String mainPoint;

    @Column(columnDefinition = "TEXT")
    private String mainPointQuote;

    @Column(columnDefinition = "TEXT")
    private String mainPointFix;

    @Column(columnDefinition = "TEXT")
    private String expressionQuote;

    @Column(columnDefinition = "TEXT")
    private String expressionFix;

    @Column(columnDefinition = "TEXT")
    private String accuracyQuote;

    @Column(columnDefinition = "TEXT")
    private String accuracyFix;

    @Column(columnDefinition = "TEXT")
    private String fluency;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String contentQuote;

    @Column(columnDefinition = "TEXT")
    private String contentFix;

    @Column(columnDefinition = "TEXT")
    private String overall;

    private String overallGrade;

    private Integer expressionScore;
    private Integer accuracyScore;
    private Integer mainPointScore;
    private Integer fluencyScore;
    private Integer contentScore;

    @Column(columnDefinition = "TEXT")
    private String improvements;

    @Column(columnDefinition = "TEXT")
    private String modelAnswer;

    @Column(columnDefinition = "TEXT")
    private String modelAnswerComment;

    @CreationTimestamp
    private LocalDateTime createdAt;

    // PERF-01: 홈/학습분석/오늘/시험계획 화면은 점수·등급·유형 같은 요약 필드만 쓰고 STT/진단/
    // quote/fix/모범답변 같은 TEXT 컬럼 15개는 전혀 렌더링하지 않는다. 그런데도 전체 이력을
    // findByMemberIdOrderByCreatedAtDesc(memberId)로 무제한 로드하면 이 TEXT까지 매번 다
    // 끌고 온다. JPQL constructor expression으로 요약 필드만 SELECT하는 이 생성자를 쓰면
    // DB에서부터 TEXT 컬럼을 아예 읽지 않는다 — FeedbackResultRepository.findSummaryByMemberId 참고.
    public FeedbackResult(Long id, com.opicnic.opicnic.domain.enums.QuestionType questionType,
                          String surveyTopicName, String comboCategory, String overallGrade,
                          Integer mainPointScore, Integer expressionScore, Integer accuracyScore,
                          Integer contentScore, Integer fluencyScore, LocalDateTime createdAt) {
        this.id = id;
        this.questionType = questionType;
        this.surveyTopicName = surveyTopicName;
        this.comboCategory = comboCategory;
        this.overallGrade = overallGrade;
        this.mainPointScore = mainPointScore;
        this.expressionScore = expressionScore;
        this.accuracyScore = accuracyScore;
        this.contentScore = contentScore;
        this.fluencyScore = fluencyScore;
        this.createdAt = createdAt;
    }
}
