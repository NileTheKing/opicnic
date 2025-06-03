// src/main/java/com/opicnic/opicnic/dto/QuestionDto.java

package com.opicnic.opicnic.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDto {
    private Long id;
    private String content; // Question 엔티티의 content 필드
    private String topic;   // QuestionSet에서 가져올 주제
    private String difficulty; // QuestionSet에서 가져올 난이도
}