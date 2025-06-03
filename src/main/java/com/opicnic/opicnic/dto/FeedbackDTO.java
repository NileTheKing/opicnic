package com.opicnic.opicnic.dto;


import com.opicnic.opicnic.domain.Question;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FeedbackDTO {
    private QuestionDto question;
    private String sttText;
    private String vocabulary;
    private String grammar;
    private String mainPoint;
    //private String pronunciation;
    private String fluency;
    private String content;
    private String overall;
    private String improvements;
}
