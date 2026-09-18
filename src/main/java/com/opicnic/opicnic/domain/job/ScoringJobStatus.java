package com.opicnic.opicnic.domain.job;

// 접수 단위(attempt)의 상태. 문항별 상태는 ScoringJobItemStatus.
// CREATED: 업로드 URL 발급됨, 아직 submit 전 (오디오는 R2 pending/ 에만 있을 수 있음)
// QUEUED: submit 완료, 워커가 집어가기 전. 202를 준 시점 — 여기서부터 "완료 보장" 약속이 시작된다
// PROCESSING: 문항 중 하나라도 워커가 집어감
// COMPLETED: 전 문항 DONE
// COMPLETED_WITH_FAILURES: 전 문항 끝났으나 FAILED가 하나 이상. 재시도는 사용자가 명시적으로 누를 때만 (ADR-0001 4절)
public enum ScoringJobStatus {
    CREATED, QUEUED, PROCESSING, COMPLETED, COMPLETED_WITH_FAILURES
}
