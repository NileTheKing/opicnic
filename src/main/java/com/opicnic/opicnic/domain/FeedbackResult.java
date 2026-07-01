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
    private String fluency;

    @Column(columnDefinition = "TEXT")
    private String content;

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
}
