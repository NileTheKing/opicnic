package com.opicnic.opicnic.dto;

import java.util.List;

public record ComboQuestionsResult(
        String comboName,
        String comboPatternKey,
        String comboCategory,
        List<QuestionDto> questions
) {
}
