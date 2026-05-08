package com.opicnic.opicnic.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.util.Map;

@Service
@Slf4j
public class STTService {

    private static final String GROQ_STT_URL = "https://api.groq.com/openai/v1/audio/transcriptions";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public STTService(@Value("${spring.ai.stt.api-key}") String apiKey,
                      @Value("${spring.ai.stt.enabled:true}") boolean enabled,
                      ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.enabled = enabled;
        this.objectMapper = objectMapper;
    }

    public String sendStreamToStt(InputStream inputStream, String filename) {
        if (!enabled) {
            log.info("[MOCK] STT 스킵, 고정 텍스트 반환");
            return "I went to the beautiful park yesterday and had a great time with my best friends.";
        }

        InputStreamResource resource = new InputStreamResource(inputStream) {
            @Override public String getFilename() { return filename; }
            @Override public long contentLength() { return -1; }
        };

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", resource)
                .header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"file\"; filename=\"" + filename + "\"");
        bodyBuilder.part("model", "whisper-large-v3");
        bodyBuilder.part("response_format", "json");

        log.info("[GROQ STT] 변환 요청 시작: {}", filename);

        String responseBody = restClient.post()
                .uri(GROQ_STT_URL)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(bodyBuilder.build())
                .retrieve()
                .body(String.class);

        try {
            Map<String, Object> response = objectMapper.readValue(responseBody, new TypeReference<>() {});
            if (response == null || !response.containsKey("text")) {
                throw new RuntimeException("Groq STT 응답이 유효하지 않습니다: " + responseBody);
            }
            log.info("[GROQ STT] 변환 완료");
            return (String) response.get("text");
        } catch (Exception e) {
            throw new RuntimeException("Groq STT 응답 파싱 실패: " + responseBody, e);
        }
    }
}
