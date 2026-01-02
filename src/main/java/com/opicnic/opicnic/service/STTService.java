package com.opicnic.opicnic.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class STTService {
    private final RestClient restClient = RestClient.create("http://localhost:8000");

    //사용자로부터 받은 오디오 파일을 STT 서버로 전송
    public String sendAudioToStt(MultipartFile file) {
        try {
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            log.info("STT 요청 전송: {}", file.getOriginalFilename()); //파일이름 오류인지 디버깅
            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            // 파일을 'file' 필드로 추가
            bodyBuilder.part("file", fileResource)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"file\"; filename=\"" + file.getOriginalFilename() + "\"");

            // RestClient 요청 전송
            Map<String, Object> response = restClient.post()
                    .uri("/stt")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(bodyBuilder.build()) // Multipart data body 전송
                    .retrieve()
                    .body(Map.class); // JSON 응답을 Map으로 파싱

            // JSON 응답에서 텍스트 추출
            if (response == null || !response.containsKey("text")) {
                log.error("STT 응답이 유효하지 않음: {}", response);
                return "STT 변환 실패";
            }

            String text = (String) response.get("text");
            if (text == null || text.trim().isEmpty()) {
                log.warn("STT 결과가 비어있음: {}", response);
                return "it was pretty good. that's all.";
            }
            log.info("STT 변환 결과: {}", response.get("text"));
            return (String) response.get("text");

        } catch (Exception e) {
            log.error("STT 요청 실패: {}", e.getMessage());
            return "STT 변환 실패";}
    }
}