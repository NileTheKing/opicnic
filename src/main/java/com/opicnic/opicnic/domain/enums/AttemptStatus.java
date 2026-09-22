package com.opicnic.opicnic.domain.enums;

public enum AttemptStatus {
    IN_PROGRESS,
    // REVIEW-01: submit이 잡 행을 만드는 동안의 임시 상태. IN_PROGRESS -> FINALIZING 전이는
    // 원자적이라 동시 요청 중 정확히 하나만 이 상태로 들어와 잡을 만든다. 성공하면 SUBMITTED로 확정한다.
    FINALIZING,
    SUBMITTED,
    EXPIRED
}
