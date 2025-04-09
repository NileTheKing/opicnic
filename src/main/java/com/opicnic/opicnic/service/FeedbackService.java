package com.opicnic.opicnic.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {

    private final STTService sttService;
    private final GeminiService geminiService;

    public List<Map<String, String>> getComboFeedback(List<MultipartFile> files) throws IOException {
        List<Map<String, String>> comboFeedbackList = new ArrayList<>();

        for (MultipartFile file : files) {
            // 1. STT 처리
            String sttResult = sttService.sendAudioToStt(file);
            log.info("STT 변환 결과: {}", sttResult);

            // 2. Gemini API 호출
            Map<String, String> geminiFeedback = geminiService.getOpicFeedback(sttResult);

            // 3. STT 결과도 함께 반환
            Map<String, String> feedbackMap = new HashMap<>();
            feedbackMap.put("sttText", sttResult);  // STT 결과 추가
            feedbackMap.putAll(geminiFeedback);    // Gemini 피드백 추가

            comboFeedbackList.add(feedbackMap);
        }
        log.info("피드백 리스트: {}", comboFeedbackList);
        return comboFeedbackList;
    }
}

