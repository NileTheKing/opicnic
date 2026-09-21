package com.opicnic.opicnic.dto;


import com.opicnic.opicnic.domain.Question;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackDTO {
    private QuestionDto question;
    private String sttText;
    private String expression;
    private String expressionQuote;
    private String expressionFix;
    private String accuracy;
    private String accuracyQuote;
    private String accuracyFix;
    private String mainPoint;
    private String mainPointQuote;
    private String mainPointFix;
    private String fluency;
    private String content;
    private String contentQuote;
    private String contentFix;
    private String overall;
    private String overallGrade;
    private Integer expressionScore;
    private Integer accuracyScore;
    private Integer mainPointScore;
    private Integer fluencyScore;
    private Integer contentScore;
    private String improvements;
    private String modelAnswer;
    private String modelAnswerComment;
    private List<FeedbackTagDto> tags;

    @Builder.Default
    private boolean failed = false;
    private String errorMessage;

    // 비동기 결과 화면용: DB에 저장된 FeedbackResult → 기존 feedback.html이 그대로 그릴 수 있는 DTO.
    // 동기 경로는 세션의 DTO를 그리고, 비동기 경로는 DB를 그린다 — 템플릿은 하나.
    public static FeedbackDTO from(com.opicnic.opicnic.domain.FeedbackResult r) {
        QuestionDto question = new QuestionDto(r.getQuestionId(), r.getQuestionContent(), r.getSurveyTopicName(), r.getQuestionType());
        return FeedbackDTO.builder()
                .question(question).sttText(r.getSttText())
                .expression(r.getExpression()).expressionQuote(r.getExpressionQuote()).expressionFix(r.getExpressionFix())
                .accuracy(r.getAccuracy()).accuracyQuote(r.getAccuracyQuote()).accuracyFix(r.getAccuracyFix())
                .mainPoint(r.getMainPoint()).mainPointQuote(r.getMainPointQuote()).mainPointFix(r.getMainPointFix())
                .fluency(r.getFluency()).content(r.getContent()).contentQuote(r.getContentQuote()).contentFix(r.getContentFix())
                .overall(r.getOverall()).overallGrade(r.getOverallGrade())
                .expressionScore(r.getExpressionScore()).accuracyScore(r.getAccuracyScore()).mainPointScore(r.getMainPointScore())
                .fluencyScore(r.getFluencyScore()).contentScore(r.getContentScore())
                .improvements(r.getImprovements()).modelAnswer(r.getModelAnswer()).modelAnswerComment(r.getModelAnswerComment())
                .build();
    }
}
