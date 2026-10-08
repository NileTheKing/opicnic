package com.opicnic.opicnic.exception;

// 외부 AI 호출(STT·채점·태깅) 실패를 우리 말로 옮긴 것. 호출 경계(ExternalCallMetrics.record)가 RestClient·Spring AI
// 예외를 이걸로 바꿔 던진다 — 워커는 클라이언트 라이브러리·공급자마다 다른 예외 모양을 몰라도 된다.
// 여기엔 "무슨 일이 있었나"만 담고, "그래서 재시도하나"는 워커(ScoringWorker.classify)가 정한다.
public class ExternalCallException extends RuntimeException {

    public enum Reason {
        RATE_LIMITED,   // 429
        UNAVAILABLE,    // 5xx·타임아웃
        BAD_REQUEST,    // 400·413·415·422 — 보낸 요청(녹음 파일·입력) 자체를 거절
        PROVIDER_ERROR  // 그 밖의 4xx(401·403·404 등) — 키·모델·설정 문제
    }

    private final Reason reason;

    public ExternalCallException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
