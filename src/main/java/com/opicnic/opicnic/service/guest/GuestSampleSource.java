package com.opicnic.opicnic.service.guest;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

// classpath:guest/sample.json을 처음 쓸 때 한 번 읽는다. 파일이 없거나 깨졌으면 게스트는 빈 상태로 시작한다
@Component
@RequiredArgsConstructor
@Slf4j
public class GuestSampleSource {

    public static final String PATH = "guest/sample.json";

    private final ObjectMapper objectMapper;
    private volatile Optional<GuestSample> cached;

    public Optional<GuestSample> get() {
        Optional<GuestSample> c = cached;
        if (c == null) {
            c = load();
            cached = c;
        }
        return c;
    }

    private Optional<GuestSample> load() {
        ClassPathResource resource = new ClassPathResource(PATH);
        if (!resource.exists()) {
            log.warn("[Guest] {} 없음 — 게스트는 빈 상태로 시작합니다", PATH);
            return Optional.empty();
        }
        try (InputStream in = resource.getInputStream()) {
            return Optional.of(objectMapper.readValue(in, GuestSample.class));
        } catch (IOException e) {
            log.warn("[Guest] {} 읽기 실패 — 게스트는 빈 상태로 시작합니다", PATH, e);
            return Optional.empty();
        }
    }
}
