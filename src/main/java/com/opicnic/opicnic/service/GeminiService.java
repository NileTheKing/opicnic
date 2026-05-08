package com.opicnic.opicnic.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.dto.QuestionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.enabled:true}")
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
            log.info("[MOCK] LLM 호출 스킵, 고정 응답 반환");
            String mock = "{\"vocabulary\":\"Good usage.\",\"grammar\":\"Correct tenses.\",\"mainPoint\":\"Clear focus.\",\"fluency\":\"Smooth flow.\",\"content\":\"Relevant.\",\"overall\":\"IM2\",\"improvements\":\"Use more diverse adjectives.\"}";
            return parseResponse(mock);
        }

        Message systemMessage = new SystemMessage(SYSTEM_PROMPT);
        Message userMessage = new UserMessage(
                "다음 질문에 대한 답변입니다: " + question.getContent() + "\n사용자의 응답: " + speechText
        );

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT, null))
                .build();

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage), options);

        try {
            ChatResponse response = chatModel.call(prompt);
            AssistantMessage assistantMessage = (AssistantMessage) response.getResult().getOutput();
            String content = assistantMessage.getText();

            log.info("[Groq LLM] 피드백 생성 완료");
            return parseResponse(content);

        } catch (Exception e) {
            log.error("Groq LLM 호출 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("LLM API 호출 중 오류가 발생했습니다.", e);
        }
    }

    private Map<String, String> parseResponse(String response) {
        try {
            return objectMapper.readValue(response, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.error("LLM JSON 파싱 오류: {}", e.getMessage());
            throw new RuntimeException("LLM 응답 파싱 중 오류가 발생했습니다.", e);
        }
    }
}
