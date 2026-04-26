package com.opicnic.opicnic.service;

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
    private final RestClient restClient;
    private final boolean enabled;

    public STTService(@Value("${spring.ai.stt.base-url}") String baseUrl, 
                      @Value("${spring.ai.stt.enabled:true}") boolean enabled) {
        this.restClient = RestClient.create(baseUrl);
        this.enabled = enabled;
    }

    /**
     * [성능 최적화 버전] 스트리밍 데이터 전송
     * 바이트 배열로 변환하지 않고 InputStream 을 그대로 파이썬 서버로 전달합니다. (Relay)
     */
    public String sendStreamToStt(InputStream inputStream, String filename) {
        if (!enabled) {
            log.info("[MOCK] STT 스트리밍 릴레이를 스킵하고 고정 텍스트를 반환합니다.");
            return "I went to the beautiful park yesterday and had a great time with my best friends.";
        }

        try {
            // InputStream 을 Resource 로 래핑 (메모리에 다 올리지 않음!)
            InputStreamResource resource = new InputStreamResource(inputStream) {
                @Override
                public String getFilename() { return filename; }
                @Override
                public long contentLength() { return -1; } // 크기를 미리 알 수 없음을 명시 (Chunked Encoding 유도)
            };

            log.info("[STREAMING] STT 서버로 스트림 릴레이 시작: {}", filename);
            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("file", resource)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"file\"; filename=\"" + filename + "\"");

            Map<String, Object> response = restClient.post()
                    .uri("/stt")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(bodyBuilder.build())
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("text")) {
                log.error("STT 응답이 유효하지 않음: {}", response);
                return "STT 변환 실패";
            }

            log.info("[STREAMING] STT 변환 결과 수신 완료");
            return (String) response.get("text");

        } catch (Exception e) {
            log.error("STT 스트리밍 요청 실패: {}", e.getMessage());
            return "STT 변환 실패";
        }
    }

    // 기존 버전 (하위 호환성 유지)
    public String sendAudioToStt(org.springframework.web.multipart.MultipartFile file) {
        return "Not used in streaming mode";
    }
}
