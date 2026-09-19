package com.opicnic.opicnic.dto.job;

// POST /api/scoring-jobs 본문. "제출"은 동사 엔드포인트가 아니라 잡 리소스 생성이다 — 202 + Location.
public record ScoringJobCreateRequest(String attemptId) {
}
