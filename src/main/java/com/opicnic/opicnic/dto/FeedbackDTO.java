package com.opicnic.opicnic.dto;


import com.opicnic.opicnic.domain.Question;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
public class FeedbackDto {
    private Question question;
    private String sttText;
    private String vocabulary;
    private String grammar;
    private String pronunciation;
    private String fluency;
    private String content;
    private String overall;
    private String improvements;
}
