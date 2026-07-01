package com.opicnic.opicnic.dto;


import com.opicnic.opicnic.domain.Question;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackDTO {
    private QuestionDto question;
    private String sttText;
    private String expression;
    private String accuracy;
    private String mainPoint;
    private String fluency;
    private String content;
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

    @Builder.Default
    private boolean failed = false;
    private String errorMessage;
}
