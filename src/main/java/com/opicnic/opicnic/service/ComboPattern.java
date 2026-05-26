package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.enums.QuestionType;

import java.util.List;

public record ComboPattern(
        int order,
        List<QuestionType> questionTypes
) {
}
