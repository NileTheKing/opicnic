package com.opicnic.opicnic.dto;

public record PracticeAttemptStartRequest(
        String mode,
        String topic,
        String difficulty
) {
}
