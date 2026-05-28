package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.enums.QuestionType;

import java.util.List;

public record ComboPattern(
        List<QuestionType> questionTypes
) {
}
