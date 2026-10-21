package com.opicnic.opicnic.service;

// 외부 제공자 호출의 종류. 제공자 한도(가짜 제공자)와 우리 쪽 속도 제한기(OutboundLimiter)가 이 단위로 센다.
// 채점과 태깅은 같은 LLM 한도를 쓴다.
public enum ProviderKind {
    STT, LLM
}
