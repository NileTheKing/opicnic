package com.opicnic.opicnic.domain.job;

// 문항 하나의 상태. 저장·재시도·실패의 단위는 attempt가 아니라 문항이다 — 12개 끝내고 워커가 죽으면
// 12개는 남고 3개만 이어서 한다. 한 덩어리로 저장하면 옛 동기 구조의 finalize와 같은 문제(끝까지
// 안 가면 전부 유실)가 돌아온다.
// QUEUED: submit 됨. 워커 대기
// PROCESSING: 워커가 집음 (재시작 후 이 상태로 남아 있으면 워커가 죽은 것 → 회수 대상)
// DONE: FeedbackResult 저장 완료
// FAILED: 시도 횟수 소진. 더 안 건드린다
public enum ScoringJobItemStatus {
    QUEUED, PROCESSING, DONE, FAILED
}
