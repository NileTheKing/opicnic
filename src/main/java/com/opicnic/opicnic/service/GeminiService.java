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
            "당신은 OPIc 시험 전문 평가자입니다. 모든 텍스트 필드는 반드시 한국어로만 작성하세요.\n" +
                    "입력은 음성 STT 결과이므로 더듬음·filler words는 감안하고, 문맥에 맞지 않는 단어는 STT 오류로 간주해 크게 감점하지 마세요.\n" +
                    "\n" +
                    "【채점 기준】\n" +
                    "mainPoint (메인포인트 명확성 및 구조):\n" +
                    "  5=하나의 명확한 MP로 시작, MP→이유→예시→재연결 구조 완성\n" +
                    "  4=MP 명확하나 재연결 없거나 구조 약간 불완전\n" +
                    "  3=MP 있으나 불명확하거나 여러 개를 나열\n" +
                    "  2=MP 파악 어려움, 두서없이 나열\n" +
                    "  1=MP 없음 또는 주제와 무관\n" +
                    "\n" +
                    "vocabulary (묘사 능력 — 어려운 단어가 아닌 형용사 다양성과 비유/묘사 표현):\n" +
                    "  5=다양한 형용사와 비유/묘사 표현을 풍부하게 사용\n" +
                    "  4=형용사 사용하나 다양성 부족, 묘사 시도 있음\n" +
                    "  3=기본 어휘 위주, 형용사 가끔 사용\n" +
                    "  2=단순 어휘만, 묘사 거의 없음\n" +
                    "  1=매우 제한적인 어휘\n" +
                    "\n" +
                    "grammar (문법 정확성):\n" +
                    "  5=시제 일관, 다양한 문장 구조, 오류 거의 없음\n" +
                    "  4=소소한 오류 있으나 전달에 지장 없음\n" +
                    "  3=오류 있으나 이해 가능, 시제 가끔 불일치\n" +
                    "  2=잦은 오류로 이해 어려움\n" +
                    "  1=기본 문법도 불안정\n" +
                    "\n" +
                    "fluency (발화량 및 흐름):\n" +
                    "  5=충분한 발화량, 자연스럽게 이어지는 흐름\n" +
                    "  4=발화량 적당, 흐름 대체로 자연스러움\n" +
                    "  3=발화량 보통, 간헐적 끊김\n" +
                    "  2=발화량 부족, 자주 끊김\n" +
                    "  1=매우 짧거나 단편적\n" +
                    "\n" +
                    "content (내용 구성 — 주제 부합도 및 이유/예시 전개):\n" +
                    "  5=주제 완전 부합, 이유+예시 충분히 전개\n" +
                    "  4=주제 부합, 전개 약간 부족\n" +
                    "  3=주제 부합하나 단순한 수준\n" +
                    "  2=주제와 부분적으로만 관련\n" +
                    "  1=주제와 무관\n" +
                    "\n" +
                    "improvements: 위 평가를 바탕으로 지금 당장 실천할 수 있는 가장 중요한 개선점 1가지만 구체적으로 작성\n" +
                    "overallGrade: IL, IM1, IM2, IM3, IH, AL 중 하나만\n" +
                    "\n" +
                    "아래 JSON 형식을 철저히 지켜 응답하세요:\n" +
                    "{\n" +
                    "  \"mainPoint\": \"메인포인트 평가\",\n" +
                    "  \"mainPointScore\": 3,\n" +
                    "  \"vocabulary\": \"어휘/묘사 능력 평가\",\n" +
                    "  \"vocabularyScore\": 3,\n" +
                    "  \"grammar\": \"문법 평가\",\n" +
                    "  \"grammarScore\": 3,\n" +
                    "  \"fluency\": \"발화량 및 흐름 평가\",\n" +
                    "  \"fluencyScore\": 3,\n" +
                    "  \"content\": \"내용 구성 평가\",\n" +
                    "  \"contentScore\": 3,\n" +
                    "  \"overall\": \"전반적인 평가\",\n" +
                    "  \"overallGrade\": \"IM2\",\n" +
                    "  \"improvements\": \"가장 중요한 개선점 1가지\"\n" +
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
                .temperature(0.0)
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
