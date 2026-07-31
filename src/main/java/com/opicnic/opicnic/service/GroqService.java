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
public class GroqService {

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
                    "    Feeling : 구체적인 감정/반응. 단순 'I like/love'는 Feeling이 아님. 최소 'I feel so relaxed', 'it makes me so happy' 수준이어야 함.\n" +
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
            String mock = "{\"mainPoint\":\"메인포인트가 명확합니다.\",\"mainPointScore\":3,\"mainPointQuote\":\"\",\"mainPointFix\":\"\"," +
                    "\"expression\":\"어휘 사용이 적절합니다.\",\"expressionScore\":3,\"expressionQuote\":\"\",\"expressionFix\":\"\"," +
                    "\"accuracy\":\"시제가 올바릅니다.\",\"accuracyScore\":3,\"accuracyQuote\":\"\",\"accuracyFix\":\"\"," +
                    "\"fluencyScore\":0," +
                    "\"content\":\"내용이 관련성 있습니다.\",\"contentScore\":3,\"contentQuote\":\"\",\"contentFix\":\"\"," +
                    "\"improvements\":\"첫 문장에 MP를 먼저 던진 뒤 감각적 형용사로 묘사를 풍부하게 해보세요.\"," +
                    "\"improvementsQuote\":\"I go to the gym every day.\",\"improvementsFix\":\"Going to the gym is honestly my favorite part of the day.\"," +
                    "\"modelAnswer\":\"My favorite place to jog is the park near my apartment. It's really spacious and peaceful, with tall trees lining the path and a small lake in the middle. I usually go there early in the morning when it's quiet, and it just feels so refreshing. I think it's the perfect spot to clear my head before starting the day.\"," +
                    "\"modelAnswerComment\":\"MP는 첫 문장 'My favorite place is...'로 시작. 형용사로 'spacious', 'peaceful', 'refreshing'을 사용해 감각적 묘사. TYPE_1 묘사 유형의 핵심인 느낌 기반 설명을 적용.\"}";

            return parseResponse(mock);
        }

        String exampleInstruction = "\n\n【변경: example을 구조화된 필드로 분리】\n" +
                "mainPoint/expression/accuracy/content 텍스트에는 '예) ...' 문장을 넣지 마라. 대신 진단만 쓰고, 인용-개선 쌍은 아래 별도 필드로 내라.\n" +
                "각 Quote/Fix는 개선 예시가 있을 때만 채우고, 없으면(이미 좋음) 빈 문자열로 둬라.\n\n" +
                "최종 JSON은 다음 형태여야 한다 (기존 필드 절대 생략 금지):\n" +
                "{\n" +
                "  \"mainPoint\": \"진단만 (예시 문장 없이)\", \"mainPointScore\": 3,\n" +
                "  \"mainPointQuote\": \"실제 발화 인용 또는 빈 문자열\", \"mainPointFix\": \"개선 문장 또는 빈 문자열\",\n" +
                "  \"expression\": \"진단만\", \"expressionScore\": 3,\n" +
                "  \"expressionQuote\": \"...\", \"expressionFix\": \"...\",\n" +
                "  \"accuracy\": \"진단만\", \"accuracyScore\": 3,\n" +
                "  \"accuracyQuote\": \"...\", \"accuracyFix\": \"...\",\n" +
                "  \"fluencyScore\": 0,\n" +
                "  \"content\": \"진단만\", \"contentScore\": 3,\n" +
                "  \"contentQuote\": \"...\", \"contentFix\": \"...\",\n" +
                "  \"improvements\": \"패턴 관찰만 (예시 문장 없이)\",\n" +
                "  \"improvementsQuote\": \"...\", \"improvementsFix\": \"...\",\n" +
                "  \"modelAnswer\": \"모범 답변 영어 텍스트\", \"modelAnswerComment\": \"한국어 설명\"\n" +
                "}";

        Message systemMessage = new SystemMessage(SYSTEM_PROMPT + exampleInstruction);
        Message userMessage = new UserMessage(
                "문제 유형: " + question.getQuestionType().name() + "\n" +
                "질문: " + question.getContent() + "\n" +
                "사용자 응답: " + speechText
        );

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT, null))
                .temperature(0.0)
                .maxTokens(3000)
                .build();

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage), options);
        ChatResponse response = chatModel.call(prompt);
        log.info("[TOKEN-DEBUG][Individual+StructuredExamples] {}", response.getMetadata().getUsage());
        return parseResponse(((AssistantMessage) response.getResult().getOutput()).getText());
    }

    // 개별 피드백 텍스트 1건에서 카테고리별 태그를 추출 (Call1을 대체할 대상 — 향후엔 getOpicFeedback 호출 시점에 함께 추출)
    public String extractFeedbackTags(String questionType, String mainPoint, String expression, String accuracy, String content) {
        if (!aiEnabled) {
            return "{\"mainPoint\":[],\"expression\":{\"vocab\":[\"VOCAB_BASIC\"],\"sentence\":[],\"imagery\":[]},\"accuracy\":[],\"content\":[]}";
        }

        boolean groupA = List.of("TYPE_1", "TYPE_2", "TYPE_3", "TYPE_4", "TYPE_8").contains(questionType);
        boolean groupC = List.of("TYPE_9", "TYPE_10").contains(questionType);

        String mainPointOptions = groupA
                ? "[\"WHY_MISSING\",\"FEELING_MISSING\",\"MP_LATE\",\"MP_GOOD\"]"
                : groupC ? "[\"FRAME_UNCLEAR\",\"FRAME_LATE\",\"FRAME_GOOD\"]" : "[] (롤플레이 유형, 태그 없음)";

        String contentOptions = switch (questionType) {
            case "TYPE_1" -> "[\"DESCRIPTION_SHALLOW\",\"CONTENT_GOOD\"]";
            case "TYPE_2" -> "[\"CLUE_MISSING\",\"CONTENT_GOOD\"]";
            case "TYPE_3" -> "[\"STORY_STRUCTURE_WEAK\",\"TIMELINE_UNCLEAR\",\"CONTENT_GOOD\"]";
            case "TYPE_4" -> "[\"CLUE_MISSING\",\"REASON_SHALLOW\",\"CONTENT_GOOD\"]";
            case "TYPE_5", "TYPE_6" -> "[\"DIALOGUE_UNNATURAL\",\"QUESTION_COUNT_SHORT\",\"CONTENT_GOOD\"]";
            case "TYPE_7" -> "[\"ALTERNATIVE_LACKING\",\"CONTENT_GOOD\"]";
            case "TYPE_8" -> "[\"SITUATION_VAGUE\",\"RESOLUTION_MISSING\",\"CONTENT_GOOD\"]";
            case "TYPE_9" -> "[\"FRAME_MISSING\",\"ONE_SIDED\",\"CONTENT_GOOD\"]";
            case "TYPE_10" -> "[\"OPINION_MISSING\",\"REASON_LACKING\",\"CONTENT_GOOD\"]";
            default -> "[\"CONTENT_GOOD\"]";
        };

        Message systemMessage = new SystemMessage(
                "당신은 OPIc 피드백 텍스트를 정해진 태그로 분류하는 분류기입니다. 아래 4개 필드 텍스트를 읽고, 각 카테고리에 해당하는 태그를 목록에서만 골라라. 텍스트에 실제로 근거가 있는 태그만 골라라. 하나의 카테고리에 여러 개 해당하면 배열에 여러 개 넣어라. 없으면 빈 배열.\n\n" +
                "카테고리별 선택 가능 태그:\n" +
                "- mainPoint: " + mainPointOptions + "\n" +
                "- expression.vocab(어휘 수준): [\"VOCAB_BASIC\",\"VOCAB_RICH\"]\n" +
                "- expression.sentence(문장 복잡도): [\"SENTENCE_SIMPLE\",\"SENTENCE_VARIED\"]\n" +
                "- expression.imagery(감각적 표현/비유): [\"IMAGERY_FLAT\",\"IMAGERY_VIVID\"]\n" +
                "  (vocab/sentence/imagery는 전부 표현력 하나의 하위 축이라 expression 객체 밑에 중첩해서 넣어라, 최상위에 따로 두지 마라)\n" +
                "- accuracy: [\"TENSE_ERROR\",\"ARTICLE_ERROR\",\"PREPOSITION_ERROR\",\"SUBJECT_VERB_ERROR\"] (오류 없으면 빈 배열, NO_ERROR 태그 쓰지 마라)\n" +
                "- content: " + contentOptions + "\n\n" +
                "JSON만 반환 (expression은 중첩 객체, 나머지는 최상위 배열):\n" +
                "{\"mainPoint\":[],\"expression\":{\"vocab\":[],\"sentence\":[],\"imagery\":[]},\"accuracy\":[],\"content\":[]}"
        );
        Message userMessage = new UserMessage(
                "mainPoint: " + mainPoint + "\nexpression: " + expression + "\naccuracy: " + accuracy + "\ncontent: " + content
        );

        // 태깅은 이미 정리된 진단 텍스트를 정해진 태그 목록 중에서 고르는 닫힌 분류 작업이라
        // 채점/코칭 작성(자유 생성)보다 훨씬 가벼움 — 더 저렴하고 TPD 여유 있는 모델로 분리해
        // 무거운 생성 작업의 일일 토큰 한도를 아낀다.
        Prompt prompt = new Prompt(List.of(systemMessage, userMessage),
                OpenAiChatOptions.builder()
                        .model("llama-3.1-8b-instant")
                        .temperature(0.0)
                        .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT, null))
                        .maxTokens(1500)
                        .build());

        ChatResponse response = chatModel.call(prompt);
        log.info("[TOKEN-DEBUG][Tag] {}", response.getMetadata().getUsage());
        return ((AssistantMessage) response.getResult().getOutput()).getText();
    }

    // Call 2: 구조화된 태그 집계 결과만으로 코칭 리포트 작성 (Call1 없이, 글쓰기 전용)
    public String getCoachingReport(String tagSummary, String targetGrade) {
        if (!aiEnabled) {
            return "{\"summary\":\"메인포인트 전달이 가장 약합니다. 최근 답변 다수에서 이유/감정 표현 없이 사실만 나열하는 패턴이 보입니다.\"," +
                    "\"strength\":\"문법 오류는 거의 없어 정확성 면에서는 안정적입니다.\"," +
                    "\"criteria\":[{\"name\":\"메인포인트\",\"analysis\":\"이유나 감정 표현 없이 사실만 나열하는 경우가 많습니다.\",\"advice\":\"답변 초반에 이유를 붙이는 연습을 해보세요. 한 문장이 길어지면 duration을 놓치기 쉬우니 두 문장으로 나눠보세요.\"}]," +
                    "\"types\":[{\"typeKey\":\"TYPE_9\",\"pattern\":\"비교 프레임 없이 대상만 나열하는 경우가 반복됩니다.\"}]}";
        }

        Message systemMessage = new SystemMessage(
                "당신은 OPIc " + targetGrade + " 달성 전문 코치입니다. 아래는 이미 코드로 집계된 확정 사실입니다 — 【요소명】 섹션 헤더 아래에 그 요소에서 실제로 반복된 태그와 발생 빈도, 대표 인용 예시가, 【유형: TYPE_9】 같은 섹션 헤더 아래에 그 유형에서 실제로 반복된 태그와 빈도가 정리돼 있습니다. 당신의 역할은 사실 확인이나 분류가 아니라, 이 사실을 근거로 자연스러운 한국어 코칭 문단을 쓰는 것입니다.\n\n" +
                "작성 순서 (반드시 이 순서로):\n" +
                "  1. 입력에서 【요소명】으로 시작하는 헤더가 몇 개인지 세어라 (숫자만 기억, 내용을 옮겨적지 마라).\n" +
                "  2. 【유형: ...】으로 시작하는 헤더가 몇 개인지 세어라 (숫자만).\n" +
                "  3. 1번 개수만큼 criteria를, 2번 개수만큼 types를 작성해라. 각 항목의 analysis/advice/pattern은 그 섹션의 태그/카운트를 근거로 삼되, **반드시 새로 지은 자연스러운 한국어 문장이어야 한다 — 원본의 'TAG_NAME: n/m건' 형식을 그대로 옮겨적는 것은 절대 금지. 영어 인용문(quote/fix, 'actual quote' -> 'improved version' 같은 것)도 절대 넣지 마라 — 그건 화면에서 별도 카드로 이미 보여주고 있어서 여기 또 넣으면 중복이다.**\n" +
                "  4. 최종 출력 직전에 criteria 개수가 1번 개수와, types 개수가 2번 개수와 정확히 같은지 다시 확인해라. 다르면 빠진 걸 추가해라.\n" +
                "  5. 최종 출력 직전에 summary/strength/analysis/advice/pattern 텍스트 전체를 다시 훑어서, WHY_MISSING·VOCAB_BASIC처럼 대문자와 밑줄로 된 태그 코드가 그대로 남아있는지 확인해라. 남아있으면 그 문장을 자연스러운 한국어로 다시 써서 교체해라.\n\n" +
                "규칙:\n" +
                "- criteria는 입력에 등장한 【요소명】 섹션당 정확히 1개씩만 만들어라. 섹션이 2개면 criteria도 2개, 섹션이 없으면 criteria도 없다. 입력에 없는 섹션(예: 【정확성】 헤더가 안 보이면)은 criteria에 절대 추가하지 마라 — 좋다는 말도, 빈 advice도 넣지 말고 그냥 통째로 빼라.\n" +
                "- criteria의 name은 섹션 헤더에 있는 요소명을 정확히 그대로 써라 (예: '메인포인트', '표현력', '정확성', '내용 구성') — 절대 다른 표현으로 바꾸거나 풀어쓰지 마라.\n" +
                "- types는 입력에 등장한 【유형: ...】 섹션당 정확히 1개씩만 만들어라. 섹션이 하나도 없으면 types도 반드시 빈 배열. **입력에 등장하지 않은 유형은 네가 알고 있는 지식이 있더라도 절대 추가하지 마라** — types의 개수는 입력의 【유형: ...】 섹션 개수와 정확히 같아야 한다.\n" +
                "- types의 typeKey는 섹션 헤더에 있는 TYPE_9 같은 코드를 그대로 써라 (한글 이름으로 바꾸지 마라). pattern은 해당 유형 섹션의 태그/카운트를 근거로 새로 쓴 한국어 문장이어야 한다 — 'TAG_NAME: n/m건' 같은 원본 형식을 그대로 붙여넣지 마라. strategy 필드는 절대 만들지 마라 — 그건 코드가 붙인다.\n" +
                "- 한 섹션 안에 태그가 여러 개면 analysis 한 문장 안에서 같이 언급해라.\n" +
                "- 주어진 태그/카운트 외의 내용을 지어내지 마라.\n" +
                "- analysis는 관찰된 패턴 진단만, advice는 일반적인 개선 전략만 써라 — 둘 다 특정 문장을 인용하지 말고, 다음에 다른 문장에도 적용할 수 있는 수준으로 일반화해서 써라.\n" +
                "- advice는 지시만 하지 말고 이유를 한 문장 포함해라.\n" +
                "- generic 조언 금지.\n" +
                "- summary/strength/analysis/advice/pattern의 한국어 설명 부분은 반드시 한국어로만 써라. 영어 인용문/예시를 제외하고는 다른 언어(영어 단어 나열, 아랍어, 중국어 등)를 절대 섞지 마라.\n" +
                "- 태그 코드(WHY_MISSING, VOCAB_BASIC, MP_LATE 같은 대문자 스네이크케이스)를 summary/strength/analysis/advice/pattern 어디에도 그대로 쓰지 마라. 반드시 자연스러운 한국어 문장으로 풀어서 설명해라.\n" +
                "  예) \"WHY_MISSING, FEELING_MISSING과 같은 문제가 관찰됨\" (금지) → \"이유나 감정 표현 없이 사실만 나열하는 경우가 많음\" (허용)\n" +
                "  예) \"VOCAB_BASIC이 관찰됨\" (금지) → \"기본적인 단어 위주로 답변함\" (허용)\n\n" +
                "【출력 형식 — JSON만】\n" +
                "{\n" +
                "  \"summary\": \"전체 패턴 2문장 요약\",\n" +
                "  \"strength\": \"잘하고 있는 점 1가지\",\n" +
                "  \"criteria\": [{\"name\": \"메인포인트\", \"analysis\": \"...\", \"advice\": \"...\"}],\n" +
                "  \"types\": [{\"typeKey\": \"TYPE_9\", \"pattern\": \"...\"}]\n" +
                "}"
        );
        Message userMessage = new UserMessage(tagSummary);

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage),
                OpenAiChatOptions.builder()
                        .temperature(0.2)
                        .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT, null))
                        .maxTokens(3000)
                        .build());

        ChatResponse response = chatModel.call(prompt);
        log.info("[TOKEN-DEBUG][Call2] {}", response.getMetadata().getUsage());
        return ((AssistantMessage) response.getResult().getOutput()).getText();
    }

    // 갭필: getCoachingReport가 요소 하나를 빠뜨렸을 때, 그 요소 하나만 콕 집어 다시 씀.
    // 셀 게 없는(딱 1개짜리) 요청이라 "몇 개 중 몇 개를 빠뜨렸는지" 실수 자체가 구조적으로 불가능함.
    public Map<String, Object> writeCriterion(String elementName, String elementLines, String targetGrade) {
        if (!aiEnabled) {
            return Map.of("analysis", "반복되는 패턴이 관찰됩니다.", "advice", "관련 표현을 연습해보세요.");
        }

        Message systemMessage = new SystemMessage(
                "당신은 OPIc " + targetGrade + " 달성 전문 코치입니다. 아래는 '" + elementName + "' 요소에서 이미 코드로 집계된 확정 사실(반복된 태그, 발생 빈도, 대표 인용 예시)입니다. 이 사실을 근거로 analysis(패턴 설명)와 advice(구체적 개선법)를 한국어로 작성하세요.\n\n" +
                "규칙:\n" +
                "- 주어진 태그/카운트/예시 외의 내용을 지어내지 마라.\n" +
                "- advice 영어 예시는 반드시 주어진 example 값을 그대로 써라. example이 없으면 예시 없이 조언만 작성해라.\n" +
                "- advice는 지시만 하지 말고 이유를 한 문장 포함해라.\n" +
                "- generic 조언 금지.\n" +
                "- 태그 코드(대문자 스네이크케이스)를 그대로 쓰지 말고 자연스러운 한국어 문장으로 풀어써라.\n" +
                "- 한국어로만 써라 (영어 인용문/예시 제외).\n\n" +
                "JSON만: {\"analysis\": \"...\", \"advice\": \"...\"}"
        );
        Message userMessage = new UserMessage(elementLines);

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage),
                OpenAiChatOptions.builder()
                        .temperature(0.2)
                        .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT, null))
                        .build());

        ChatResponse response = chatModel.call(prompt);
        log.info("[TOKEN-DEBUG][CriterionGapFill-{}] {}", elementName, response.getMetadata().getUsage());
        return parseResponse(((AssistantMessage) response.getResult().getOutput()).getText());
    }

    // 갭필: getCoachingReport가 유형 하나를 빠뜨렸을 때, 그 유형 하나만 콕 집어 다시 씀.
    public String writeTypePattern(String typeKey, String typeLabel, String typeLines) {
        if (!aiEnabled) {
            return "{\"pattern\":\"반복되는 패턴이 관찰됩니다.\"}";
        }

        Message systemMessage = new SystemMessage(
                "당신은 OPIc 코치입니다. 아래는 '" + typeKey + "(" + typeLabel + ")' 유형에서 이미 코드로 집계된 확정 사실(반복된 태그, 발생 빈도)입니다. 이 사실을 근거로 pattern(패턴 설명)을 한국어로 작성하세요.\n\n" +
                "규칙:\n" +
                "- 주어진 태그/카운트 외의 내용을 지어내지 마라.\n" +
                "- 태그 코드(대문자 스네이크케이스)를 그대로 쓰지 말고 자연스러운 한국어 문장으로 풀어써라.\n" +
                "- 한국어로만 써라.\n\n" +
                "JSON만: {\"pattern\": \"...\"}"
        );
        Message userMessage = new UserMessage(typeLines);

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage),
                OpenAiChatOptions.builder()
                        .temperature(0.2)
                        .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT, null))
                        .build());

        ChatResponse response = chatModel.call(prompt);
        log.info("[TOKEN-DEBUG][TypeGapFill-{}] {}", typeKey, response.getMetadata().getUsage());
        return ((AssistantMessage) response.getResult().getOutput()).getText();
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
