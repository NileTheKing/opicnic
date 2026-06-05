package com.opicnic.opicnic.domain.attempt;

import com.opicnic.opicnic.domain.enums.AttemptStatus;
import com.opicnic.opicnic.domain.enums.PracticeMode;

import java.time.Instant;
import java.util.List;

public record PracticeAttempt(
        String attemptId,
        List<Long> questionIds,  // null 항목 = 자기소개 (DB에 없는 고정 문제)
        Long memberId,
        PracticeMode mode,
        String comboPatternKey,
        String comboCategory,
        Instant expiresAt,
        AttemptStatus status
) {
    public boolean isExpired() {
        return status == AttemptStatus.EXPIRED || Instant.now().isAfter(expiresAt);
    }

    public PracticeAttempt withStatus(AttemptStatus newStatus) {
        return new PracticeAttempt(attemptId, questionIds, memberId, mode, comboPatternKey, comboCategory, expiresAt, newStatus);
    }
}
