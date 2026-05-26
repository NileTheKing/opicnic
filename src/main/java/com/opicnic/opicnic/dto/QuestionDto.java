package com.opicnic.opicnic.dto;

import com.opicnic.opicnic.domain.Question;
import com.opicnic.opicnic.domain.enums.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDto {
    private Long id;
    private String content;
    private String topic;
    private QuestionType questionType;

    public static QuestionDto from(Question question) {
        return new QuestionDto(
                question.getId(),
                question.getContent(),
                question.getQuestionSet().getTopic().getLabel(),
                question.getQuestionType()
        );
    }
}
