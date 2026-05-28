package com.opicnic.opicnic.dto;

import java.util.List;

public record PracticeFeedbackResponse(
        List<PracticeFeedbackItem> results,
        List<Integer> failedIndexes,
        boolean complete,
        String resultUrl
) {
}
