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

    @Value("${LLM_MOCK_DELAY_MS:0}")
    private long mockDelayMs;

    private static final String SYSTEM_PROMPT =
            "당신은 OPIc 시험 전문 평가자입니다. 반드시 한국어로만 응답하세요.\n" +
                    "평가 기준: 확실한 메인포인트, 시제, 문법, 어휘, 발화량. 적절한 filler word는 감점 요소가 아닙니다.\n" +
                    "음성 STT 결과이므로 더듬음·filler words는 감안하고, 문맥에 맞지 않는 단어는 STT 오류로 간주해 크게 감점하지 마세요.\n" +
                    "각 항목을 평가하고, 아래 JSON 형식을 철저히 지켜 응답하세요.\n" +
                    "score 필드는 1(매우 미흡)~5(매우 우수) 정수입니다.\n" +
                    "overallGrade는 OPIc 등급 문자열(IL, IM1, IM2, IM3, IH, AL)만 단독으로 입력하세요.\n" +
                    "{\n" +
                    "  \"vocabulary\": \"어휘에 대한 평가 (한국어)\",\n" +
                    "  \"vocabularyScore\": 3,\n" +
                    "  \"grammar\": \"문법에 대한 평가 (한국어)\",\n" +
                    "  \"grammarScore\": 3,\n" +
                    "  \"mainPoint\": \"메인포인트에 대한 평가 (한국어)\",\n" +
                    "  \"mainPointScore\": 3,\n" +
                    "  \"fluency\": \"발화량 및 유창성에 대한 평가 (한국어)\",\n" +
                    "  \"fluencyScore\": 3,\n" +
                    "  \"content\": \"내용 구성에 대한 평가 (한국어)\",\n" +
                    "  \"contentScore\": 3,\n" +
                    "  \"overall\": \"전반적인 영어 능력 평가 (한국어)\",\n" +
                    "  \"overallGrade\": \"IM2\",\n" +
                    "  \"improvements\": \"개선점에 대한 구체적인 조언 (한국어)\"\n" +
                    "}";

    public Map<String, Object> getOpicFeedback(String speechText, QuestionDto question) {
        if (!aiEnabled) {
            if (mockDelayMs > 0) {
                try { Thread.sleep(mockDelayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            log.info("[MOCK] LLM 호출 스킵, 고정 응답 반환 (delay={}ms)", mockDelayMs);
            String mock = "{\"vocabulary\":\"어휘 사용이 적절합니다.\",\"vocabularyScore\":3,\"grammar\":\"시제가 올바릅니다.\",\"grammarScore\":3,\"mainPoint\":\"메인포인트가 명확합니다.\",\"mainPointScore\":3,\"fluency\":\"발화가 자연스럽습니다.\",\"fluencyScore\":3,\"content\":\"내용이 관련성 있습니다.\",\"contentScore\":3,\"overall\":\"전반적으로 중급 수준입니다.\",\"overallGrade\":\"IM2\",\"improvements\":\"더 다양한 형용사를 사용해보세요.\"}";
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

    private Map<String, Object> parseResponse(String response) {
        try {
            return objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("LLM JSON 파싱 오류: {}", e.getMessage());
            throw new RuntimeException("LLM 응답 파싱 중 오류가 발생했습니다.", e);
        }
    }
}
