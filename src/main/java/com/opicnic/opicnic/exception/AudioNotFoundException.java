package com.opicnic.opicnic.exception;

// 스토리지에 녹음 파일이 없다. 구현(R2의 NoSuchKeyException, 인메모리)과 상관없이 이것 하나로 던진다
public class AudioNotFoundException extends RuntimeException {

    public AudioNotFoundException(String key, Throwable cause) {
        super("녹음 파일 없음: " + key, cause);
    }
}
