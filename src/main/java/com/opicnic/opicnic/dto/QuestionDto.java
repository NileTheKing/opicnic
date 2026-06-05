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
    private String surveyTopicName;

    public QuestionDto(Long id, String content, String topic, QuestionType questionType) {
        this.id = id;
        this.content = content;
        this.topic = topic;
        this.questionType = questionType;
    }

    public static QuestionDto from(Question question) {
        QuestionDto dto = new QuestionDto(
                question.getId(),
                question.getContent(),
                question.getQuestionSet().getTopic().getLabel(),
                question.getQuestionType()
        );
        dto.setSurveyTopicName(question.getQuestionSet().getTopic().name());
        return dto;
    }
}
