package com.opicnic.opicnic.dto;

import com.opicnic.opicnic.domain.enums.SurveyTopic;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QuestionSetApiDto(Long id, @NotBlank String name, @NotNull SurveyTopic topic) {
}
