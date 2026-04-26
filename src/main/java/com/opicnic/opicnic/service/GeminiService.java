package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.dto.QuestionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.model.ChatModel;
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

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.ai.google.genai.enabled:true}")
    private boolean aiEnabled;

    private static final String SYSTEM_PROMPT =
            "OPIC 시험의 평가 기준에 따라 사용자의 영어 음성을 평가합니다.(확실한 메인포인트, 풍부한 감정표현, 시제, 문법, 어휘, 발화량으로 평가. 적절한 filler word는 감점 요소가 아닙니다.)\n" +
                    "사용자의 응답은 음성이 stt된 것이므로 더듬은 부분이나 filler words는 감안해야합니다.:\n" +
                    "문맥에 맞지 않는 단어가 나온다면 stt가 잘못된 것일수도 있으니 평가점수에서 크기 감점을 하지 않도록 한다\n" +
                    "문제에 대한 사용자의 영어 음성 응답 텍스트를 분석하고 다음 형식을 철저히 지켜 JSON 으로 응답해주세요\n" +
                    "{\n" +
                    "  \"vocabulary\": \"어휘에 대한 평가\",\n" +
                    "  \"grammar\": \"문법에 대한 평가\",\n" +
                    "  \"mainPoint\": \"메인포인트에 대한 평가\",\n" +
                    "  \"fluency\": \"발화량 및 유창성에 대한 평가\",\n" +
                    "  \"content\": \"내용 구성에 대한 평가\",\n" +
                    "  \"overall\": \"전반적인 영어 능력 평가 (IL, IM, IH, AL 중 하나 포함)\",\n" +
                    "  \"improvements\": \"개선점에 대한 구체적인 조언\"\n" +
                    "}";

    public Map<String, String> getOpicFeedback(String speechText, QuestionDto question) {
        if (!aiEnabled) {
            log.info("[MOCK] Gemini API 호출을 스킵하고 고정 응답을 반환합니다.");
            String mockResponse = "{\"vocabulary\":\"Good usage of basic words.\", \"grammar\":\"Correct tenses used.\", \"mainPoint\":\"Clear focus.\", \"fluency\":\"Smooth flow.\", \"content\":\"Relevant information.\", \"overall\":\"IM2 - Your speaking is natural and understandable.\", \"improvements\":\"Try using more diverse adjectives.\"}";
            return parseResponse(mockResponse);
        }

        // 시스템 프롬프트 및 사용자 메시지 생성
        Message systemMessage = new SystemMessage(SYSTEM_PROMPT);
        Message userMessage = new UserMessage(
                "다음 질문에 대한 답변입니다: " + question.getContent() + "\n" +
                        "사용자의 응답: " + speechText
        );

        // 프롬프트 생성
        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

        try {
            // Gemini 모델에 요청 보내고 응답 받기
            ChatResponse response = chatModel.call(prompt);

            // 응답에서 텍스트 추출
            String responseContent;

            //직접 첫 번째 메시지 내용 가져오기
            AssistantMessage assistantMessage = (AssistantMessage) response.getResult().getOutput();
            responseContent = assistantMessage.getText()
                    .replaceAll("(?m)^```json\\s*", "")  // ```json 윗줄 제거
                    .replaceAll("(?m)^```\\s*", "");     // ``` 아랫줄 제거;

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
