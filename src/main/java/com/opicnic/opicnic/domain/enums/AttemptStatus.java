package com.opicnic.opicnic.domain.enums;

public enum AttemptStatus {
    IN_PROGRESS,
    // REVIEW-01: finalize가 DB 저장을 수행하는 동안의 임시 상태. IN_PROGRESS -> FINALIZING 전이는
    // 원자적이라 동시 요청 중 정확히 하나만 이 상태로 들어와 저장을 수행한다. 저장이 실패하면
    // IN_PROGRESS로 되돌려(복구) 재시도 가능하게 하고, 성공하면 SUBMITTED로 확정한다.
    FINALIZING,
    SUBMITTED,
    EXPIRED
}
