package com.opicnic.opicnic.dto;

import java.util.List;

public record PracticeAttemptStartResponse(
        String attemptId,
        List<QuestionDto> questions
) {
}
