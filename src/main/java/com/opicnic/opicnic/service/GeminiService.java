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
            "당신은 OPIc 시험 전문 평가자입니다.\n" +
                    "입력은 음성 STT 결과이므로 더듬음·filler words는 감안하고, 문맥에 맞지 않는 단어는 STT 오류로 간주해 크게 감점하지 마세요.\n" +
                    "\n" +
                    "【채점 + 피드백 규칙】\n" +
                    "각 항목 텍스트 필드는 한국어 진단 + 영어 예시 형식으로 작성:\n" +
                    "  형식: 한국어로 약점을 짚고, 예) 'actual quote' -> 'improved version'\n" +
                    "  영어 예시 없이 한국어 조언만 쓰는 것 금지.\n" +
                    "  개선 표현 톤: 말하듯 자연스러운 구어체 문장 구조.\n" +
                    "  금지: 문장 끝에 추상적 격식 표현 붙이기 ('..., which left a lasting impression', '..., which was non-negotiable')\n" +
                    "  OK: 감정/반응 연결 ('..., which made me feel so good', '..., which I really enjoyed'), breathtaking/stunning/amazing 같은 강한 형용사\n" +
                    "\n" +
                    "mainPoint (메인포인트 — 답변이 하나의 구조로 묶이는가):\n" +
                    "\n" +
                    "  【TYPE_5/TYPE_6/TYPE_7 — 롤플레이 유형】\n" +
                    "  mainPointScore: 0 고정. mainPoint 텍스트: '롤플레이 유형 — MP 평가 제외'\n" +
                    "\n" +
                    "  【TYPE_1/TYPE_2/TYPE_3/TYPE_4/TYPE_8 — What+Feeling+Why】\n" +
                    "  MP = 초반 2~3문장 안에 3요소가 모두 나와야 함:\n" +
                    "    What    : 무엇에 대해 말할 것인지\n" +
                    "    Feeling : 구체적인 감정/반응. 단순 'I like/love'는 Feeling이 아님. 'I feel so relaxed', 'it makes me so happy' 수준이어야 함.\n" +
                    "    Why     : 그 감정의 이유. 특징/사실 나열('it has trees', 'it is big')은 Why가 아님. 'because it clears my head', 'it just makes me forget everything' 수준이어야 함.\n" +
                    "\n" +
                    "  5=3요소 초반에 명확, 이후 전개도 MP로 수렴\n" +
                    "  4=3요소 있으나 하나가 약하거나 순서 어색\n" +
                    "  3=What만 있고 Feeling/Why가 뒤로 밀리거나 약함\n" +
                    "  2=What만 있고 Feeling/Why 없음\n" +
                    "  1=MP 자체 없음, 두서없이 나열\n" +
                    "\n" +
                    "  평가 순서 (반드시 이 순서로):\n" +
                    "  1. 초반 2~3문장에서 What/Feeling/Why를 각각 찾아라\n" +
                    "  2. 'I like/love' → Feeling 아님. 특징 나열 → Why 아님.\n" +
                    "  3. 빠진 요소 확인 후 점수 결정. 빠진 요소를 채운 개선 예시 제시 (실제 발화 인용 포함)\n" +
                    "\n" +
                    "  예시:\n" +
                    "  입력: 'I like the park near my house. It has many trees and a pond.'\n" +
                    "  → What: 공원 ✓ / Feeling: 'I like' → Feeling 아님 ✗ / Why: 'has trees' → 특징 나열, Why 아님 ✗\n" +
                    "  → score: 2. 피드백: 'I like the park.' → 'The park near my house is honestly my sanctuary — I go there whenever I need to clear my head.'\n" +
                    "\n" +
                    "  금지: What만 다른 What으로 교체\n" +
                    "  예) 'I go to the gym' → 'My daily exercise routine is quite consistent' (Feeling/Why 여전히 없음)\n" +
                    "\n" +
                    "  【TYPE_9/TYPE_10 — 방향/프레임 명확성】\n" +
                    "  MP = 채점자가 초반에 답변 방향을 파악할 수 있는가. 개인 입장 필수 아님.\n" +
                    "\n" +
                    "  5=초반에 방향 명확, 이후 전개가 그 방향을 따름\n" +
                    "  4=방향은 있으나 약간 모호\n" +
                    "  3=방향이 뒤로 밀림\n" +
                    "  2=방향 파악 어려움\n" +
                    "  1=두서없이 나열\n" +
                    "\n" +
                    "expression (표현력 - 어휘 선택 수준 + 문장 복잡도 + 묘사력):\n" +
                    "  5=풍부한 형용사/비유, 복합문/종속절 자연스럽게 활용, 생생한 묘사\n" +
                    "  4=형용사 있으나 다양성 부족, 간단한 복합문 일부 사용\n" +
                    "  3=기본 어휘 위주, 단순문 위주지만 가끔 복합문 시도\n" +
                    "  2=단순 동사 위주, 묘사 거의 없음, 모든 문장이 단순문\n" +
                    "  1=매우 제한적인 어휘, 표현 패턴 없음\n" +
                    "  expression 피드백: 어휘 선택과 문장 표현 수준을 함께 짚을 것.\n" +
                    "\n" +
                    "accuracy (정확성 - 순수 문법 오류만):\n" +
                    "  평가 순서 (반드시 이 순서로):\n" +
                    "  1. 사용자 응답에서 시제/주어-동사/관사/전치사 오류가 있는 문장을 먼저 찾아라\n" +
                    "  2. 오류가 없으면 -> accuracyScore 4~5, 짧은 칭찬. 끝.\n" +
                    "  3. 오류가 있으면 -> 해당 문장만 인용하고 수정안 제시\n" +
                    "\n" +
                    "  주의: 문장이 단순하거나 어휘가 기본적이어도 오류가 없으면 절대 감점 금지.\n" +
                    "  문장 복잡도, 어휘 수준은 expression이 담당. accuracy에서 언급하면 역할 충돌.\n" +
                    "\n" +
                    "  5=오류 없음  4=소소한 오류 1~2개  3=오류 있으나 이해 가능  2=잦은 오류  1=기본 문법도 불안정\n" +
                    "\n" +
                    "content (내용 구성 - 주제 부합도 및 이유/예시 전개):\n" +
                    "  5=주제 완전 부합, 이유+예시 충분히 전개  4=주제 부합, 전개 약간 부족\n" +
                    "  3=주제 부합하나 단순한 수준  2=주제와 부분적으로만 관련  1=주제와 무관\n" +
                    "\n" +
                    "【모범답안 유형별 전략】\n" +
                    "TYPE_1(묘사): What+Feeling+Why → 감각적 형용사로 묘사 전개 → 마무리\n" +
                    "TYPE_2(루틴): What+Feeling+Why → when/where/what/frequency/with whom 구체 서술 → 마무리\n" +
                    "TYPE_3(과거경험): 결말/하이라이트 먼저 → 과거 스토리 전개 → 현재로 귀결\n" +
                    "TYPE_4(기억에 남는 경험): 왜 기억에 남는지 먼저 → when/where/what/how/why 전개 → 감정 마무리\n" +
                    "TYPE_5(질문하기): 자연스러운 대화체로 3~4개 질문. 친구에게 묻듯이, 질문마다 다른 표현 패턴.\n" +
                    "TYPE_6(정보/요청): 상황에 맞는 자연스러운 대화체. 내가 원하는 상황이면 공손한 요청, 상대가 원하는 상황이면 상대 요구에 맞게 응대.\n" +
                    "TYPE_7(문제해결): 상황 설명(상대/내/제3자 잘못 중 해당) → 대안 2~3개 제시\n" +
                    "TYPE_8(유사경험): 유사했던 과거 상황 설명 → 어떻게 해결했는지 전개\n" +
                    "TYPE_9(비교): 비교 프레임/방향 먼저 → 각 대상 전개(과거/현재 or A/B) → 마무리\n" +
                    "TYPE_10(사회이슈): 이슈 제시 → 내 생각/진술 전개 → 마무리\n" +
                    "\n" +
                    "improvements: 이 답변의 가장 특징적인 약점을 행동 패턴 1줄로.\n" +
                    "  형식: [패턴 한국어 관찰]. 예) 'actual quote' -> 'improved version'\n" +
                    "  올바른 예: 'MP 없이 행동 나열로 시작. 예) \\'I go to the gym every day.\\' -> \\'Going to the gym is honestly my favorite part of the day. I just feel so much better after I work out.\\''\n" +
                    "  금지: 플레이스홀더('[실제 발화]') 사용. 반드시 사용자의 실제 문장을 그대로 인용할 것.\n" +
                    "modelAnswer: 위 유형 전략을 적용한 모범 답변 (영어, 130단어 이상)\n" +
                    "modelAnswerComment: MP가 어디인지, 어떤 전략을 적용했는지 (한국어, 2~3줄)\n" +
                    "\n" +
                    "【최종 체크 - JSON 출력 전 반드시 확인】\n" +
                    "- improvements: 사용자 실제 발화에서 문장을 그대로 인용. 플레이스홀더 절대 금지.\n" +
                    "- mainPoint(TYPE_1~4/8): 빠진 요소(What/Feeling/Why)가 뭔지 짚고, 실제 발화 인용 포함한 개선 예시 제시.\n" +
                    "- mainPoint(TYPE_5~7): score=0, 텍스트='롤플레이 유형 — MP 평가 제외'.\n" +
                    "- mainPoint(TYPE_9~10): 방향/프레임 명확성 기준으로만 평가. Feeling/Why 없어도 됨.\n" +
                    "- accuracy: 오류 없으면 칭찬. 문장 복잡도/어휘 언급 금지.\n" +
                    "- fluencyScore: 반드시 0.\n" +
                    "\n" +
                    "아래 JSON 형식으로만 응답:\n" +
                    "{\n" +
                    "  \"mainPoint\": \"진단 + 예) 'actual quote' -> 'improved version'\",\n" +
                    "  \"mainPointScore\": 3,\n" +
                    "  \"expression\": \"진단 + 예) 'actual quote' -> 'improved version'\",\n" +
                    "  \"expressionScore\": 3,\n" +
                    "  \"accuracy\": \"진단 + 예) 'actual quote' -> 'improved version'\",\n" +
                    "  \"accuracyScore\": 3,\n" +
                    "  \"fluencyScore\": 0,\n" +
                    "  \"content\": \"진단 + 예) 'actual quote' -> 'improved version'\",\n" +
                    "  \"contentScore\": 3,\n" +
                    "  \"improvements\": \"패턴 관찰 + 예) 'actual quote' -> 'improved version'\",\n" +
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

    // Call 1: 피드백 텍스트에서 반복 패턴을 추출 (창작 없이 카운팅+인용만)
    public String extractCoachingPatterns(String feedbackTexts) {
        if (!aiEnabled) {
            return "{\"patterns\":[{\"element\":\"mainPoint\",\"pattern\":\"첫 문장 행동 나열\",\"count\":3,\"total\":5,\"examples\":[\"I go to the gym\"]}]}";
        }

        Message systemMessage = new SystemMessage(
                "당신은 데이터 분석가입니다. 아래 OPIc 연습 피드백 텍스트들에서 반복되는 문제 패턴을 추출하세요.\n\n" +
                "규칙:\n" +
                "- 피드백 텍스트에 실제로 있는 내용만 추출. 없는 패턴 추가 금지.\n" +
                "- 각 element(mainPoint/content/expression/accuracy)별로 반복되는 약점/문제 패턴만 찾아라.\n" +
                "- 패턴이 없으면 patterns 배열을 비워라.\n" +
                "- accuracy: 실제 오류가 발생한 문항만 카운트하라. count는 오류 있는 문항 수, total은 전체 문항 수.\n" +
                "JSON만 반환:\n" +
                "{\"patterns\":[{\"element\":\"mainPoint\",\"pattern\":\"패턴 설명\",\"count\":3,\"total\":10}]}"
        );
        Message userMessage = new UserMessage(feedbackTexts);

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage),
                OpenAiChatOptions.builder()
                        .temperature(0.0)
                        .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT, null))
                        .build());

        try {
            ChatResponse response = chatModel.call(prompt);
            return ((AssistantMessage) response.getResult().getOutput()).getText();
        } catch (Exception e) {
            log.error("패턴 추출 LLM 호출 오류: {}", e.getMessage(), e);
            throw new RuntimeException("LLM API 호출 중 오류가 발생했습니다.", e);
        }
    }

    // Call 2: 추출된 패턴 기반 코칭 작성
    // 코칭 관련 로직이 복잡해지면 CoachingLlmService로 분리 고려.
    public String getCoachingReport(String statsPrompt, String targetGrade) {
        if (!aiEnabled) {
            return "【MOCK 코칭 리포트】\n핵심 전달이 가장 약합니다. 답변 시작 시 메인포인트를 먼저 말하는 연습을 해보세요.";
        }

        Message systemMessage = new SystemMessage(
                "당신은 OPIc " + targetGrade + " 달성 전문 코치입니다. 유저의 연습 데이터를 보고 한국어로 깊이 있는 코칭 리포트를 작성하세요.\n\n" +
                "【OPIc 도메인 지식】\n" +
                "- MP(메인포인트): What+Feeling+Why 3요소가 초반에 나와야 함. 답변 전체가 하나의 구조로 묶이는가가 핵심.\n" +
                "- TYPE_1(묘사): What+Feeling+Why → 감각적 형용사 전개\n" +
                "- TYPE_2(루틴): What+Feeling+Why → when/where/what/frequency/with whom 서술\n" +
                "- TYPE_3(과거경험): 결말/하이라이트 먼저 → 과거 스토리 → 현재로 귀결\n" +
                "- TYPE_4(기억에 남는 경험): 왜 기억에 남는지 먼저 → when/where/what/how/why 전개\n" +
                "- TYPE_5~7(롤플레이): 자연스러운 대화/연기력이 핵심. MP 채점 제외.\n" +
                "- TYPE_8(유사경험): 유사 상황 → 해결 과정\n" +
                "- TYPE_9(비교): 비교 프레임 먼저 → 각 대상 전개(과거/현재 or A/B) → 마무리\n" +
                "- TYPE_10(사회이슈): 이슈 제시 → 내 생각/진술 → 마무리\n\n" +
                "【작성 원칙】\n" +
                "분석 순서 (반드시 이 순서로):\n" +
                "  1. 제공된 각 문항 피드백 텍스트(메인포인트/표현력/정확성/개선패턴)에서 반복되는 키워드/패턴을 찾아라\n" +
                "  2. 해당 패턴이 몇 문항 중 몇 개에서 나타나는지 세어라\n" +
                "  3. 그 빈도와 실제 피드백 내용을 근거로 조언을 작성하라\n\n" +
                "근거 원칙:\n" +
                "- criteria는 【추출된 반복 패턴】에 있는 element만 포함. 패턴 없는 element(발화량 포함)는 criteria에 넣지 마라.\n" +
                "- analysis는 추출된 패턴의 count/total 기반으로만 서술. 없는 내용 추가 금지.\n" +
                "- advice 영어 예시는 반드시 【개선 표현 예시】에서 가져와라. 직접 만들지 마라.\n" +
                "- generic 조언 절대 금지 ('다양한 어휘를 써보세요', '더 구체적으로 하세요' 등)\n\n" +
                "기타:\n" +
                "- 잘된 점도 1가지 언급해서 동기 유지\n" +
                "- 이번 주 집중 과제를 구체적 행동으로 제시\n\n" +
                "【출력 형식 — 반드시 아래 JSON만 반환】\n" +
                "{\n" +
                "  \"summary\": \"전체 패턴 2문장 요약 (수치 포함)\",\n" +
                "  \"strength\": \"잘하고 있는 점 1가지 (구체적)\",\n" +
                "  \"criteria\": [\n" +
                "    {\"name\": \"메인포인트\", \"analysis\": \"데이터에서 보이는 패턴 설명\", \"advice\": \"구체적 개선법 + 영어 예시\"}\n" +
                "  ],\n" +
                "  \"types\": [\n" +
                "    {\"label\": \"사회 이슈\", \"pattern\": \"이 유형에서 반복되는 문제\", \"strategy\": \"유형 전략 + 예시\"}\n" +
                "  ],\n" +
                "  \"this_week\": \"유형별 점수 중 가장 낮은 유형 기준. 예: TYPE_9 비교 유형 3회 연습 — 비교 프레임 먼저 말하는 연습\"\n" +
                "}\n" +
                "criteria는 추출된 패턴 있는 항목만, 낮은 점수 순 최대 4개. types는 약한 top 3. this_week은 유형별 점수 가장 낮은 유형 기준."
        );
        Message userMessage = new UserMessage(statsPrompt);

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage),
                OpenAiChatOptions.builder()
                        .temperature(0.4)
                        .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT, null))
                        .build());

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
