package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private final VertexAiGeminiChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();


    private static final String SYSTEM_PROMPT =
            """
            당신은 OPIC 영어 시험 평가자입니다.
            사용자의 영어 음성 텍스트를 분석하고 다음 형식의 JSON으로 응답해주세요
            주의: 응답은 반드시 JSON 코드 블록 없이 JSON 형식 그대로 전달해주세요. 백틱(```)을 포함하지 마세요.
            :
            {
              "vocabulary": "어휘에 대한 평가",
              "grammar": "문법에 대한 평가",
              "pronunciation": "발음에 대한 평가",
              "fluency": "유창성에 대한 평가",
              "content": "내용 구성에 대한 평가",
              "overall": "전반적인 영어 능력 평가 (IL, IM, IH, AL 중 하나 포함)",
              "improvements": "개선점에 대한 구체적인 조언"
            }
        
            가능한 모든 항목에 구체적인 평가를 한국어로 작성해주세요.
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

            // 응답에서 텍스트 추출
            String responseContent;

            //직접 첫 번째 메시지 내용 가져오기
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

    //JSON 응답 파싱
    private Map<String, String> parseResponse(String response) {
        try {
            return objectMapper.readValue(response, Map.class);
        } catch (Exception e) {
            log.error("Gemini JSON 파싱 오류: {}", e.getMessage());
            Map<String, String> errorMap = new HashMap<>();
            errorMap.put("error", "Gemini JSON 응답 파싱 중 오류가 발생했습니다: " + e.getMessage());
            return errorMap;
        }
    }
}

