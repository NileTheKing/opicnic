package com.opicnic.opicnic.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class STTService {

    private static final String GROQ_STT_URL = "https://api.groq.com/openai/v1/audio/transcriptions";

    // S2 실패 주입용 — 실측한 Groq 429 본문(docs/performance/slo.md).
    private static final String MOCK_429_BODY =
            "Rate limit reached for model `whisper-large-v3` in organization ... on requests per minute (RPM): "
                    + "Limit 20, Used 20, Requested 1. Please try again in 3s.";
    private static final String MOCK_5XX_BODY = "Service Unavailable";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final boolean enabled;
    private final long mockDelayMs;
    // volatile: dev 스위치(DevPracticeController /mock-failures)가 실행 중에 바꾼다 — 앱 재시작 없이 "평소 → 장애 → 복구"를 재현
    private volatile double mock429Rate;
    private volatile double mock5xxRate;
    private volatile double mockTimeoutRate;

    // mock 경로(enabled=false)에서만 의미 있다. 운영(enabled=true)에선 이 값을 읽지 않는다
    public void setMockFailureRates(double rate429, double rate5xx, double rateTimeout) {
        this.mock429Rate = rate429;
        this.mock5xxRate = rate5xx;
        this.mockTimeoutRate = rateTimeout;
    }

    public STTService(RestClient.Builder restClientBuilder,
                      @Value("${spring.ai.stt.api-key}") String apiKey,
                      @Value("${spring.ai.stt.enabled:true}") boolean enabled,
                      @Value("${STT_MOCK_DELAY_MS:0}") long mockDelayMs,
                      @Value("${STT_MOCK_429_RATE:0}") double mock429Rate,
                      @Value("${STT_MOCK_5XX_RATE:0}") double mock5xxRate,
                      @Value("${STT_MOCK_TIMEOUT_RATE:0}") double mockTimeoutRate,
                      ObjectMapper objectMapper,
                      MeterRegistry meterRegistry) {
        // restClientBuilder는 Spring Boot가 spring.http.client.* 타임아웃 설정을 적용해 관리하는 빈이다.
        // RestClient.builder()를 직접 호출하면 이 전역 타임아웃을 상속받지 못한다.
        this.restClient = restClientBuilder
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.enabled = enabled;
        this.mockDelayMs = mockDelayMs;
        this.mock429Rate = mock429Rate;
        this.mock5xxRate = mock5xxRate;
        this.mockTimeoutRate = mockTimeoutRate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    public String sendStreamToStt(byte[] audioBytes, String filename) {
        // mock 경로도 같이 센다 — S2 호출 증폭을 지표 비율로 재기 위해 (ExternalCallMetrics 참고).
        return ExternalCallMetrics.record(meterRegistry, "stt", () -> callStt(audioBytes, filename));
    }

    private String callStt(byte[] audioBytes, String filename) {
        if (!enabled) {
            if (mockDelayMs > 0) {
                try { Thread.sleep(mockDelayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            // S2 측정용 실패 주입 — STT_MOCK_429_RATE/STT_MOCK_5XX_RATE/STT_MOCK_TIMEOUT_RATE가 0이면(기본값) 기존과 동일하게 항상 성공.
            double roll = ThreadLocalRandom.current().nextDouble();
            if (roll < mock429Rate) {
                log.info("[MOCK] STT 실패 주입 (429)");
                throw HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                        HttpHeaders.EMPTY, MOCK_429_BODY.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            }
            if (roll < mock429Rate + mock5xxRate) {
                log.info("[MOCK] STT 실패 주입 (503)");
                throw HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                        HttpHeaders.EMPTY, MOCK_5XX_BODY.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            }
            if (roll < mock429Rate + mock5xxRate + mockTimeoutRate) {
                // RestClient read-timeout이 실제로 던지는 형태(ResourceAccessException ← SocketTimeoutException).
                // 실제라면 read-timeout(60s)만큼 기다린 뒤 나지만, 여기서는 mockDelayMs만 쓰고 바로 던진다 —
                // S2가 재는 건 호출 횟수(증폭)이지 대기 시간이 아니다.
                log.info("[MOCK] STT 실패 주입 (timeout)");
                throw new ResourceAccessException("I/O error on POST request for \"" + GROQ_STT_URL + "\": Read timed out",
                        new SocketTimeoutException("Read timed out"));
            }
            log.info("[MOCK] STT 스킵, 고정 텍스트 반환 (delay={}ms)", mockDelayMs);
            return "I went to the beautiful park yesterday and had a great time with my best friends.";
        }

        ByteArrayResource resource = new ByteArrayResource(audioBytes) {
            @Override public String getFilename() { return filename; }
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
