package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.enums.QuestionType;

import java.util.List;

public record ComboPattern(
        String name,
        List<QuestionType> questionTypes
) {
    public String patternKey() {
        return questionTypes.stream()
                .map(QuestionType::name)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    public String category() {
        if (questionTypes.contains(QuestionType.TYPE_6) || questionTypes.contains(QuestionType.TYPE_7)) return "C3";
        if (questionTypes.contains(QuestionType.TYPE_9) || questionTypes.contains(QuestionType.TYPE_10)) return "C5";
        if (questionTypes.contains(QuestionType.TYPE_5)) return "C4";
        if (questionTypes.contains(QuestionType.TYPE_4)) return "C2";
        return "C1";
    }
}
