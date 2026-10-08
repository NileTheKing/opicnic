package com.opicnic.opicnic.exception;

// AI 모델(STT·LLM) 응답이 약속한 형식을 어겼다 — JSON 파싱 실패, 필드 누락, 점수 범위 밖.
// 예전엔 IllegalStateException으로 던져서, 우리 코드의 일반 버그도 "모델이 형식을 어김"으로 분류됐다
public class InvalidModelOutputException extends RuntimeException {

    public InvalidModelOutputException(String message) {
        super(message);
    }

    public InvalidModelOutputException(String message, Throwable cause) {
        super(message, cause);
    }
}
