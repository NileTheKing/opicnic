package com.opicnic.opicnic.dto;

public record PracticeFeedbackItem(
        int questionIndex,
        FeedbackDTO feedback
) {
}
