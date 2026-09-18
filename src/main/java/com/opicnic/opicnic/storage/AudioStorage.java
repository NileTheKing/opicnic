package com.opicnic.opicnic.storage;

import java.net.URL;
import java.time.Duration;

// 오디오 오브젝트 스토리지 추상화 (ADR-0001). 운영 구현은 R2(S3 API), 테스트는 인메모리.
// 서버는 바이트를 받지 않는다 — 클라이언트가 presigned URL로 직접 올리고, 워커가 읽는다.
public interface AudioStorage {

    // 클라이언트가 PUT할 수 있는 서명 URL. contentLength·contentType을 서명에 포함시켜
    // 그 크기·타입이 아니면 스토리지가 거절하게 한다 — 서버 측 4겹 방어선을 우회하는 직접 업로드의 상한.
    URL presignPut(String key, long contentLength, String contentType, Duration ttl);

    byte[] read(String key);

    boolean exists(String key);

    void delete(String key);
}
