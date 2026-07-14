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
}
