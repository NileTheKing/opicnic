package com.opicnic.opicnic.dto.job;

// 녹음이 끝난 문항의 크기·타입. 서명에 그대로 박히므로 브라우저는 정확히 이 크기로 PUT해야 한다.
public record UploadUrlRequest(int index, long size, String contentType) {
}
