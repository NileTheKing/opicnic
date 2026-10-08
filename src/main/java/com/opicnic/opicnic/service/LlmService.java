package com.opicnic.opicnic.service;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.exception.InvalidModelOutputException;
import io.micrometer.core.instrument.MeterRegistry;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

// 채점·태깅·코칭 LLM 호출. OpenAI 호환 주소면 공급자는 상관없다 — 어떤 공급자를 쓸지는 application-<공급자>.yml(LLM_PROVIDER).
// 예전 이름 GroqService (2026-10-08 Gemini 전환 준비로 이름을 바꿈)
@Service
@RequiredArgsConstructor
@Slf4j
public class LlmService {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Value("${spring.ai.openai.enabled:true}")
    private boolean aiEnabled;

    @Value("${LLM_MOCK_DELAY_MS:0}")
    private long mockDelayMs;

    // volatile: dev 스위치(DevPracticeController /mock-failures)가 실행 중에 바꾼다
    @Value("${LLM_MOCK_429_RATE:0}")
    private volatile double mock429Rate;

    @Value("${LLM_MOCK_5XX_RATE:0}")
    private volatile double mock5xxRate;

    @Value("${LLM_MOCK_TIMEOUT_RATE:0}")
    private volatile double mockTimeoutRate;

    // mock 경로(enabled=false)에서만 의미 있다. 운영(enabled=true)에선 이 값을 읽지 않는다
    public void setMockFailureRates(double rate429, double rate5xx, double rateTimeout) {
        this.mock429Rate = rate429;
        this.mock5xxRate = rate5xx;
        this.mockTimeoutRate = rateTimeout;
    }

    @Value("${spring.ai.tagging.model:openai/gpt-oss-20b}")
    private String taggingModel;

    // gpt-oss(Groq)는 reasoning 토큰을 줄이려고 low를 준다. 빈 값이면 보내지 않는다 — 보정 실험의 Gemini 측정이 이 조건
    @Value("${opicnic.llm.reasoning-effort:low}")
    private String reasoningEffort;

    // S2 실패 주입용 — 실측한 Groq 429 본문(docs/performance/slo.md).
    private static final String MOCK_429_BODY =
            "Rate limit reached for model `openai/gpt-oss-120b` in organization ... on tokens per minute (TPM): "
                    + "Limit 8000, Used 4271, Requested 7120. Please try again in 24.9s.";
    private static final String MOCK_5XX_BODY = "Service Unavailable";

    // 채점 프롬프트와 응답 스키마는 파일로 둔다 — 읽고 고치기 쉽게, 보정 실험(run.py --prompt-file)도 같은 파일을 쓴다.
    // 등급(level) 판단 + 코칭 4개 항목 + 이렇게 바꿔보세요 + 모범 답안을 한 번에. 스키마로 형식을 강제해 JSON이 깨지지 않는다
    // (docs/performance/2026-10-07-grading-calibration, 2026-10-08 덧붙인 지시 3개를 하나로 다시 씀)
    static final String SCORING_PROMPT = resource("prompts/scoring.md");
    static final String SCORING_SCHEMA = resource("prompts/scoring-schema.json");

    private static String resource(String path) {
        try (var in = LlmService.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("리소스 없음: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public Map<String, Object> getOpicFeedback(String speechText, QuestionDto question) {
        // mock 경로도 같이 센다 — S2 호출 증폭을 지표 비율로 재기 위해 (ExternalCallMetrics 참고).
        return ExternalCallMetrics.record(meterRegistry, "score", () -> callOpicFeedback(speechText, question));
    }

    private Map<String, Object> callOpicFeedback(String speechText, QuestionDto question) {
        if (!aiEnabled) {
            MockProvider.admit();
            if (mockDelayMs > 0) {
                try { Thread.sleep(mockDelayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            // S2 측정용 실패 주입 — LLM_MOCK_429_RATE/LLM_MOCK_5XX_RATE/LLM_MOCK_TIMEOUT_RATE가 0이면(기본값) 기존과 동일하게 항상 성공.
            double roll = ThreadLocalRandom.current().nextDouble();
            if (roll < mock429Rate) {
                log.info("[MOCK] LLM 실패 주입 (429)");
                throw HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                        HttpHeaders.EMPTY, MOCK_429_BODY.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            }
            if (roll < mock429Rate + mock5xxRate) {
                log.info("[MOCK] LLM 실패 주입 (503)");
                throw HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                        HttpHeaders.EMPTY, MOCK_5XX_BODY.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            }
            if (roll < mock429Rate + mock5xxRate + mockTimeoutRate) {
                // read-timeout이 실제로 던지는 형태. 대기 시간은 흉내내지 않는다 — STTService의 같은 분기 주석 참고.
                log.info("[MOCK] LLM 실패 주입 (timeout)");
                throw new ResourceAccessException("I/O error on POST request for \"https://api.groq.com/openai/v1/chat/completions\": Read timed out",
                        new SocketTimeoutException("Read timed out"));
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

        Message systemMessage = new SystemMessage(SCORING_PROMPT);
        Message userMessage = new UserMessage(
                "문제 유형: " + question.getQuestionType().name() + "\n" +
                "질문: " + question.getContent() + "\n" +
                "사용자 응답: " + speechText
        );

        // gpt-oss 계열은 reasoning 토큰을 completion에 포함해서 출력이 llama 때보다 크게 늘었다.
        // 250~300단어 답변 실측 completion 2,574 → 3000이면 여유가 400여 토큰뿐이라 잘림(=JSON 파싱 실패) 위험.
        // reasoningEffort를 low로 낮춰 실제 출력을 줄이고(실측 2,574 → 1,022), 상한도 4096으로 올려 이중으로 막는다.
        // 상한을 올리지 않고 3000을 유지하는 이유: Groq은 max_tokens를 TPM 예약분으로 잡는다.
        // 429 본문으로 실증됨 — "Limit 8000, Used 4271, Requested 7120"에서 7120 = prompt 3024 + max_tokens 4096.
        // effort=low로 실측 completion이 1,022까지 내려와 3000도 3배 여유이고,
        // 상한을 올리면 요청당 TPM 점유만 늘어 429가 잦아진다.
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, SCORING_SCHEMA))
                .temperature(0.0)
                .reasoningEffort(reasoningEffort == null || reasoningEffort.isBlank() ? null : reasoningEffort)
                .maxTokens(3000)
                .build();

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage), options);
        ChatResponse response = chatModel.call(prompt);
        log.info("[TOKEN-DEBUG][Individual+StructuredExamples] {}", response.getMetadata().getUsage());
        return parseResponse(((AssistantMessage) response.getResult().getOutput()).getText());
    }

    // 개별 피드백 텍스트 1건에서 카테고리별 태그를 추출 (Call1을 대체할 대상 — 향후엔 getOpicFeedback 호출 시점에 함께 추출)
    public String extractFeedbackTags(String questionType, String mainPoint, String expression, String accuracy, String content) {
        return ExternalCallMetrics.record(meterRegistry, "tag",
                () -> callFeedbackTags(questionType, mainPoint, expression, accuracy, content));
    }

    private String callFeedbackTags(String questionType, String mainPoint, String expression, String accuracy, String content) {
        if (!aiEnabled) {
            MockProvider.admit();
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
                        .model(taggingModel)
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
                        // 자유 생성이라 채점보다 길어질 수 있고 여기에 gpt-oss의 reasoning 토큰까지 붙는다 — 채점과 같이 상향.
                        .maxTokens(4096)
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
            // Gemini가 영어 인용 속 작은따옴표를 백슬래시로 escape해(\\') JSON을 깨는 일이 있다(보정 실험 42건 중 1건) — 그 escape만 너그럽게 받는다
            return objectMapper.readerFor(new TypeReference<Map<String, Object>>() {})
                    .with(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER.mappedFeature())
                    .readValue(response);
        } catch (Exception e) {
            log.error("LLM JSON 파싱 오류: {}", e.getMessage());
            throw new InvalidModelOutputException("LLM 응답 파싱 중 오류가 발생했습니다.", e);
        }
    }
}
