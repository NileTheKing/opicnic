package com.opicnic.opicnic.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private final VertexAiGeminiChatModel chatModel;

    @Value("${spring.ai.vertex.ai.gemini.model-name:gemini-pro}")
    private String modelName;

    private static final String SYSTEM_PROMPT = """
            당신은 OPIC 영어 시험 평가자입니다.
            사용자의 영어 음성 텍스트를 분석하고 다음 요소를 평가해주세요:

            1. 어휘력(Vocabulary): 사용한 어휘의 다양성, 적절성, 정확성
            2. 문법(Grammar): 문법적 정확성과 자연스러움
            3. 발음과 억양(Pronunciation): 발음의 명확성과 자연스러움
            4. 유창성(Fluency): 말의 흐름, 자연스러움, 머뭇거림 없이 말하는 능력
            5. 내용 구성(Content): 주제에 대한 이해도와 응답의 적절성
            6. 종합 평가(Overall): 전반적인 영어 능력 평가 및 레벨(IL, IM, IH, AL 등으로 표기)
            7. 개선점(Improvements): 더 나은 점수를 위한 구체적인 조언

            각 평가 항목은 한국어로 작성하고, 짧지만 구체적인 피드백을 제공해주세요.
            """;

    public Map<String, String> getOpicFeedback(String speechText) {
        log.info("Gemini API에 요청 전송: {}", speechText);

        // 시스템 프롬프트 및 사용자 메시지 생성
        Message systemMessage = new SystemMessage(SYSTEM_PROMPT);
        Message userMessage = new UserMessage(speechText);

        // 프롬프트 생성
        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

        try {
            // Gemini 모델에 요청 보내고 응답 받기
            ChatResponse response = chatModel.call(prompt);

            // 응답에서 텍스트 추출 (다양한 방법 시도)
            String responseContent;

            // 방법 1: 직접 첫 번째 메시지 내용 가져오기
            AssistantMessage assistantMessage = (AssistantMessage) response.getResult().getOutput();
            responseContent = assistantMessage.getText();

            log.info("Gemini API 응답: {}", responseContent);
            return parseResponse(responseContent);

        } catch (Exception e) {
            log.error("Gemini API 호출 중 오류 발생: {}", e.getMessage(), e);
            Map<String, String> errorMap = new HashMap<>();
            errorMap.put("error", "API 호출 중 오류가 발생했습니다: " + e.getMessage());
            return errorMap;
        }
    }

    private Map<String, String> parseResponse(String response) {
        Map<String, String> feedbackMap = new HashMap<>();
        if (response == null || response.trim().isEmpty()) {
            feedbackMap.put("error", "Gemini API에서 유효한 응답을 받지 못했습니다.");
            return feedbackMap;
        }

        String[] lines = response.split("\n");
        String currentCategory = null;
        StringBuilder currentContent = new StringBuilder();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("1. 어휘력")) {
                addCategoryToMap(feedbackMap, currentCategory, currentContent);
                currentCategory = "vocabulary";
                currentContent = extractContent(line);
            } else if (line.startsWith("2. 문법")) {
                addCategoryToMap(feedbackMap, currentCategory, currentContent);
                currentCategory = "grammar";
                currentContent = extractContent(line);
            } else if (line.startsWith("3. 발음과 억양")) {
                addCategoryToMap(feedbackMap, currentCategory, currentContent);
                currentCategory = "pronunciation";
                currentContent = extractContent(line);
            } else if (line.startsWith("4. 유창성")) {
                addCategoryToMap(feedbackMap, currentCategory, currentContent);
                currentCategory = "fluency";
                currentContent = extractContent(line);
            } else if (line.startsWith("5. 내용 구성")) {
                addCategoryToMap(feedbackMap, currentCategory, currentContent);
                currentCategory = "content";
                currentContent = extractContent(line);
            } else if (line.startsWith("6. 종합 평가")) {
                addCategoryToMap(feedbackMap, currentCategory, currentContent);
                currentCategory = "overall";
                currentContent = extractContent(line);
            } else if (line.startsWith("7. 개선점")) {
                addCategoryToMap(feedbackMap, currentCategory, currentContent);
                currentCategory = "improvements";
                currentContent = extractContent(line);
            } else if (currentCategory != null) {
                currentContent.append(" ").append(line);
            }
        }

        addCategoryToMap(feedbackMap, currentCategory, currentContent);
        return feedbackMap;
    }

    private void addCategoryToMap(Map<String, String> map, String category, StringBuilder content) {
        if (category != null) {
            map.put(category, content.toString().trim());
        }
    }

    private StringBuilder extractContent(String line) {
        int colonIndex = line.indexOf(":");
        if (colonIndex != -1) {
            return new StringBuilder(line.substring(colonIndex + 1).trim());
        }
        return new StringBuilder();
    }
}