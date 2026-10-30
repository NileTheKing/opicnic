package com.opicnic.opicnic.service;

// 외부 호출 하나가 제공자의 분당 토큰 한도에서 쓰는 양. OpenAI 계산 규칙(요청마다 max(max_tokens, 입력 길이로 추정한 토큰)) 기준.
//  채점: max(3000, 입력 약 3,250) — 입력은 2026-10-22 실측 평균 3,246
//  태깅: max(1500, 입력 약 460)
//  STT: 80초 답변 약 2,400 — gpt-4o-mini-transcribe 가격표($0.003/분, 입력 $1.25 / 출력 $5 per 1M)에서 역산(출력 분당 약 200 토큰 가정)
// 가짜 제공자(실제 비용)와 미리 세는 속도 제한기(추정치)가 같이 쓴다. 실험에서는 둘이 같아 추정이 정확한 경우가 된다.
public final class CallTokens {
    public static final int SCORE = 3250;
    public static final int TAG = 1500;
    public static final int STT = 2400;

    private CallTokens() {}
}
