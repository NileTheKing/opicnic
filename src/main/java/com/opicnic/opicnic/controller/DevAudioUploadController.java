package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.storage.AudioStorage;
import com.opicnic.opicnic.storage.InMemoryAudioStorage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

// 용량·장애 실험(docs/performance/2026-10-21-capacity-outage) 전용: 메모리 저장소일 때 presigned PUT을 대신 받는다.
// 실제로는 브라우저가 R2에 직접 올리지만, 메모리 저장소엔 올릴 곳이 없다. dev 프로필에서만 열린다.
// 크기는 4MB까지(운영의 presign 상한과 같다).
@RestController
@RequiredArgsConstructor
@Profile("dev")
public class DevAudioUploadController {

    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final String PREFIX = "/api/dev/audio/";

    private final AudioStorage audioStorage;

    @PutMapping(PREFIX + "**")
    public ResponseEntity<Void> upload(HttpServletRequest request) throws IOException {
        if (!(audioStorage instanceof InMemoryAudioStorage memory)) return ResponseEntity.notFound().build();
        String key = request.getRequestURI().substring(request.getRequestURI().indexOf(PREFIX) + PREFIX.length());
        byte[] bytes = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) return ResponseEntity.status(413).build();
        memory.put(key, bytes);
        return ResponseEntity.ok().build();
    }
}
