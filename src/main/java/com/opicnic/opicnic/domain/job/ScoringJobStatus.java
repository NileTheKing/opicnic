package com.opicnic.opicnic.domain.job;

// 접수 단위(attempt)의 상태. 문항별 상태는 ScoringJobItemStatus.
// 행은 submit 시점에 QUEUED로 생긴다 — 202를 준 순간 = DB에 있는 순간 = "완료 보장" 약속의 시작.
// 그 전(문제 조립·녹음·업로드)은 Caffeine PracticeAttempt가 들고 있고, 화면만 열고 나간 건 DB에 남지 않는다.
// QUEUED: submit 완료, 워커가 집어가기 전
// PROCESSING: 문항 중 하나라도 워커가 집어감
// COMPLETED: 전 문항 DONE
// COMPLETED_WITH_FAILURES: 전 문항 끝났으나 FAILED가 하나 이상. 재시도는 사용자가 명시적으로 누를 때만 (ADR-0001 4절)
public enum ScoringJobStatus {
    QUEUED, PROCESSING, COMPLETED, COMPLETED_WITH_FAILURES
}
