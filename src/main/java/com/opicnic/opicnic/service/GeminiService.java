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
                    "【채점 기준 — 문제 유형과 무관하게 적용】\n" +
                    "mainPoint (메인포인트 명확성 및 구조):\n" +
                    "  5=첫 문장에 명확한 MP, MP→부가설명→결론 구조 완성\n" +
                    "  4=MP 명확하나 구조 약간 불완전\n" +
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
                    "fluency (발화량 — STT 텍스트 단어 수 기준):\n" +
                    "  5=130단어 이상 (약 1분20초+, 자연스러운 흐름)\n" +
                    "  4=90~129단어\n" +
                    "  3=60~89단어\n" +
                    "  2=30~59단어\n" +
                    "  1=29단어 이하\n" +
                    "\n" +
                    "content (내용 구성 — 주제 부합도 및 이유/예시 전개):\n" +
                    "  5=주제 완전 부합, 이유+예시 충분히 전개\n" +
                    "  4=주제 부합, 전개 약간 부족\n" +
                    "  3=주제 부합하나 단순한 수준\n" +
                    "  2=주제와 부분적으로만 관련\n" +
                    "  1=주제와 무관\n" +
                    "\n" +
                    "improvements: 채점 결과를 바탕으로 지금 당장 실천할 수 있는 가장 중요한 개선점 1가지. 구체적으로.\n" +
                    "\n" +
                    "【모범답안 생성 — 아래 유형별 전략은 모범답안에만 적용, 채점에 영향 주지 말 것】\n" +
                    "질문 내용을 먼저 파악한 뒤 아래 유형 전략을 적용할 것.\n" +
                    "TYPE_1(묘사): MP(핵심 인상) + 감각적 형용사로 구체적 묘사 + 결론\n" +
                    "TYPE_2(루틴): MP(루틴의 특징) + 구체적인 습관/순서 묘사 + 결론\n" +
                    "TYPE_3(과거경험): MP(경험 핵심 한 줄) + 스토리 전개 + 결론\n" +
                    "TYPE_4(기억에 남는 경험): MP(왜 기억에 남는지) + 감정·상황 묘사 + 결론\n" +
                    "TYPE_5(질문하기): 질문 3개, 각각 다른 표현 패턴으로 자연스럽게\n" +
                    "TYPE_6(정보요청): 상황 설명 + 요청사항 명확히 + 공손한 마무리\n" +
                    "TYPE_7(문제해결): 문제 인식 + 구체적 대안 2개 이상 + 양해 표현\n" +
                    "TYPE_8(비슷한 경험): MP(유사점) + 스토리 전개 + 결론\n" +
                    "TYPE_9(비교): MP(핵심 입장) + 질문에서 제시한 두 대상의 구체적 대비 + 결론\n" +
                    "TYPE_10(이슈): MP(나의 입장) + 두 관점 균형있게 + 결론\n" +
                    "\n" +
                    "modelAnswer: 위 유형 전략을 적용한 모범 답변 (영어, 130단어 이상)\n" +
                    "modelAnswerComment: MP가 어디인지, 어떤 전략을 적용했는지 (한국어, 2~3줄)\n" +
                    "\n" +
                    "아래 JSON 형식을 철저히 지켜 응답하세요:\n" +
                    "{\n" +
                    "  \"mainPoint\": \"메인포인트 평가\",\n" +
                    "  \"mainPointScore\": 3,\n" +
                    "  \"vocabulary\": \"어휘/묘사 능력 평가\",\n" +
                    "  \"vocabularyScore\": 3,\n" +
                    "  \"grammar\": \"문법 평가\",\n" +
                    "  \"grammarScore\": 3,\n" +
                    "  \"fluency\": \"발화량 평가 (단어 수 포함)\",\n" +
                    "  \"fluencyScore\": 3,\n" +
                    "  \"content\": \"내용 구성 평가\",\n" +
                    "  \"contentScore\": 3,\n" +
                    "  \"overall\": \"전반적인 평가\",\n" +
                    "  \"improvements\": \"가장 중요한 개선점 1가지\",\n" +
                    "  \"modelAnswer\": \"모범 답변 영어 텍스트\",\n" +
                    "  \"modelAnswerComment\": \"모범 답변 핵심 포인트 한국어 설명\"\n" +
                    "}";

    public Map<String, Object> getOpicFeedback(String speechText, QuestionDto question) {
        if (!aiEnabled) {
            if (mockDelayMs > 0) {
                try { Thread.sleep(mockDelayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            log.info("[MOCK] LLM 호출 스킵, 고정 응답 반환 (delay={}ms)", mockDelayMs);
            String mock = "{\"vocabulary\":\"어휘 사용이 적절합니다.\",\"vocabularyScore\":3,\"grammar\":\"시제가 올바릅니다.\",\"grammarScore\":3,\"mainPoint\":\"메인포인트가 명확합니다.\",\"mainPointScore\":3,\"fluency\":\"발화가 자연스럽습니다.\",\"fluencyScore\":3,\"content\":\"내용이 관련성 있습니다.\",\"contentScore\":3,\"overall\":\"전반적으로 중급 수준입니다.\",\"improvements\":\"첫 문장에 MP를 먼저 던진 뒤 감각적 형용사로 묘사를 풍부하게 해보세요.\",\"modelAnswer\":\"My favorite place to jog is the park near my apartment. It's really spacious and peaceful, with tall trees lining the path and a small lake in the middle. I usually go there early in the morning when it's quiet, and it just feels so refreshing. I think it's the perfect spot to clear my head before starting the day.\",\"modelAnswerComment\":\"MP는 첫 문장 'My favorite place is...'로 시작. 형용사로 'spacious', 'peaceful', 'refreshing'을 사용해 감각적 묘사. TYPE_1 묘사 유형의 핵심인 느낌 기반 설명을 적용.\"}";

            return parseResponse(mock);
        }

        Message systemMessage = new SystemMessage(SYSTEM_PROMPT);
        Message userMessage = new UserMessage(
                "문제 유형: " + question.getQuestionType().name() + "\n" +
                "질문: " + question.getContent() + "\n" +
                "사용자 응답: " + speechText
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

    // 코칭 리포트는 피드백과 달리 자유 텍스트 반환. 책임이 다르지만 ChatModel 공유 목적으로 같은 클래스에 둠.
    // 코칭 관련 로직이 복잡해지면 CoachingLlmService로 분리 고려.
    public String getCoachingReport(String statsPrompt) {
        if (!aiEnabled) {
            return "【MOCK 코칭 리포트】\n핵심 전달이 가장 약합니다. 답변 시작 시 메인포인트를 먼저 말하는 연습을 해보세요.";
        }

        Message systemMessage = new SystemMessage(
                "당신은 OPIc 시험 전문 코치입니다. 유저의 연습 통계를 분석해 한국어로 코칭 리포트를 작성하세요.\n\n" +
                "【OPIc 도메인 지식】\n" +
                "- MP(메인포인트): 첫 문장에 핵심을 던지는 것. 모든 유형의 핵심.\n" +
                "- TYPE_1(묘사): MP + 감각적 형용사. TYPE_2(루틴): MP + Before→But now 비교.\n" +
                "- TYPE_3/4/8(경험): MP + 스토리. TYPE_5~7(롤플레이): 질문/요청/문제해결.\n" +
                "- TYPE_9(비교): MP + 과거→현재. TYPE_10(이슈): MP + A관점 + B관점.\n" +
                "- 고득점(IH/AL)은 MP 명확성 + 형용사 다양성 + 유형별 전략 완성도가 핵심.\n\n" +
                "【규칙】\n" +
                "- '다양한 어휘를 써보세요' 같은 generic 조언 금지\n" +
                "- 데이터에서 보이는 패턴을 구체적으로 짚을 것\n\n" +
                "【출력 형식 — 반드시 아래 JSON만 반환】\n" +
                "{\n" +
                "  \"summary\": \"전체 학습 패턴 한줄 요약\",\n" +
                "  \"criteria\": [\n" +
                "    {\"name\": \"메인포인트\", \"advice\": \"구체적 개선 조언\"}\n" +
                "  ],\n" +
                "  \"types\": [\n" +
                "    {\"typeKey\": \"TYPE_3\", \"label\": \"과거 경험\", \"advice\": \"유형 전략 기반 조언\"}\n" +
                "  ]\n" +
                "}\n" +
                "criteria는 점수가 낮거나 패턴이 보이는 항목만. types는 약한 top 3만."
        );
        Message userMessage = new UserMessage(statsPrompt);

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage),
                OpenAiChatOptions.builder().temperature(0.4).build());

        try {
            ChatResponse response = chatModel.call(prompt);
            return ((AssistantMessage) response.getResult().getOutput()).getText();
        } catch (Exception e) {
            log.error("코칭 리포트 LLM 호출 오류: {}", e.getMessage(), e);
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
