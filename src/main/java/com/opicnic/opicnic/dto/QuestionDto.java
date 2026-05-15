// src/main/java/com/opicnic/opicnic/dto/QuestionDto.java

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
    private String difficulty;
    private QuestionType questionType;

    public static QuestionDto from(Question question) {
        String topic = question.getCombo().getQuestionSet().getTopic().getLabel();
        String difficulty = question.getCombo().getQuestionSet().getDifficulty().getLabel();
        return new QuestionDto(
                question.getId(),
                question.getContent(),
                topic,
                difficulty,
                question.getQuestionType()
        );
    }
}