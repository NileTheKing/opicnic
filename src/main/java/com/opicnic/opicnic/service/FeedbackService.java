package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.dto.ComboQuestionsResult;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.FeedbackTagDto;
import com.opicnic.opicnic.dto.QuestionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {

    private final ComboPracticeService comboPracticeService;
    private final STTService sttService;
    private final GroqService groqService;
    private final ObjectMapper objectMapper;

    public ComboQuestionsResult getComboQuestions(String topic, String difficulty) {
        return comboPracticeService.getComboQuestions(topic, difficulty);
    }

    public List<FeedbackDTO> getComboFeedbackStreaming(
            List<InputStream> inputStreams, List<QuestionDto> questions) {

        if (inputStreams.size() != questions.size()) {
            throw new IllegalArgumentException(
                "음성 파일 수(" + inputStreams.size() + ")와 질문 수(" + questions.size() + ")가 일치하지 않습니다.");
        }

        log.info("[Structured Concurrency] 피드백 분석 시작 ({}개)", inputStreams.size());
        long start = System.currentTimeMillis();

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<StructuredTaskScope.Subtask<FeedbackDTO>> subtasks = new ArrayList<>();

            List<Long> subtaskDurations = new CopyOnWriteArrayList<>();

            for (int i = 0; i < inputStreams.size(); i++) {
                final int idx = i;
                final InputStream is = inputStreams.get(i);
                final QuestionDto question = questions.get(i);

                subtasks.add(scope.fork(() -> {
                    long subtaskStart = System.currentTimeMillis();
                    log.info("[Subtask-{}] STT & LLM 처리 시작 (Thread: {})", idx, Thread.currentThread());

                    byte[] audioBuffer = is.readAllBytes();
                    int maxAttempts = 3;
                    Exception lastException = null;
                    boolean lastWasRateLimited = false;

                    for (int attempt = 0; attempt < maxAttempts; attempt++) {
                        try {
                            if (attempt > 0) {
                                // 429(rate limit)는 일반 일시 오류보다 훨씬 더 기다려야 재시도가 의미 있다 —
                                // 같은 짧은 백오프로 밀어붙이면 다음 시도도 또 429를 받을 뿐이다.
                                long delay = lastWasRateLimited
                                        ? (3000L << (attempt - 1)) + ThreadLocalRandom.current().nextLong(1000)
                                        : (1000L << (attempt - 1)) + ThreadLocalRandom.current().nextLong(300);
                                log.warn("[Subtask-{}] 재시도 {}/{}, {}ms 대기{}", idx, attempt, maxAttempts - 1, delay,
                                        lastWasRateLimited ? " (rate limit 감지, 대기 연장)" : "");
                                Thread.sleep(delay);
                            }

                            String speechText = sttService.sendStreamToStt(
                                    new ByteArrayInputStream(audioBuffer), "audio_" + idx + ".webm");
                            if (speechText == null || speechText.trim().split("\\s+").length < 5) {
                                subtaskDurations.add(System.currentTimeMillis() - subtaskStart);
                                return noResponseDto(question, speechText);
                            }
                            if (question.getQuestionType() == null) {
                                // 자기소개는 DB Question이 아니라 고정 문항이라 QuestionType이 없다.
                                // 실제 시험에서도 자기소개는 채점 문항으로 취급되지 않으므로 TYPE_1~10 rubric에
                                // 맞지 않는 채점/태깅 LLM 호출 없이 완료 처리한다. DB 저장 자체를 하지 않도록
                                // PracticeAttemptApiController.saveFeedbackResults()에서 questionType==null을 걸러내며,
                                // 이 필터링 덕분에 "총 문항 수"/"최근 기록"/"코칭 열람 조건" 등 문항 개수 기반
                                // 통계에도 섞이지 않는다.
                                subtaskDurations.add(System.currentTimeMillis() - subtaskStart);
                                return selfIntroductionDto(question, speechText);
                            }
                            var feedbackMap = groqService.getOpicFeedback(speechText, question);

                            String mainPointDiag = str(feedbackMap, "mainPoint");
                            String expressionDiag = str(feedbackMap, "expression");
                            String accuracyDiag = str(feedbackMap, "accuracy");
                            String contentDiag = str(feedbackMap, "content");

                            String tagsJson = groqService.extractFeedbackTags(
                                    question.getQuestionType().name(),
                                    mainPointDiag, expressionDiag, accuracyDiag, contentDiag);
                            List<FeedbackTagDto> tags = parseTags(tagsJson, question.getQuestionType().name());

                            long subtaskMs = System.currentTimeMillis() - subtaskStart;
                            subtaskDurations.add(subtaskMs);
                            log.info("[Subtask-{}] 완료: {}ms{}", idx, subtaskMs,
                                    attempt > 0 ? " (재시도 " + attempt + "회)" : "");

                            int fluencyScore = computeFluencyScore(speechText);
                            // TYPE_5~7(롤플레이)은 MP를 "평가 제외"로 0 고정 반환하도록 프롬프트에 지시했다.
                            // 이 0을 다른 4개 점수와 그대로 평균 내면 롤플레이를 연습할수록 등급이 구조적으로
                            // 낮아진다 (SCORE-02). null로 바꿔두면 computeGrade/computeOverallText/
                            // ExamPlanService.weightedAvg가 이미 null을 평균 분모에서 제외하므로 자동으로 해결된다.
                            boolean mpExcluded = isRoleplayType(question.getQuestionType());
                            Integer mpScore = mpExcluded ? null : score(feedbackMap, "mainPointScore");
                            int exScore    = score(feedbackMap, "expressionScore");
                            int acScore    = score(feedbackMap, "accuracyScore");
                            int ctScore    = score(feedbackMap, "contentScore");
                            String grade   = computeGrade(mpScore, exScore, acScore, fluencyScore, ctScore);

                            String mainPointQuote = str(feedbackMap, "mainPointQuote");
                            String mainPointFix = str(feedbackMap, "mainPointFix");
                            String expressionQuote = str(feedbackMap, "expressionQuote");
                            String expressionFix = str(feedbackMap, "expressionFix");
                            String accuracyQuote = str(feedbackMap, "accuracyQuote");
                            String accuracyFix = str(feedbackMap, "accuracyFix");
                            String contentQuote = str(feedbackMap, "contentQuote");
                            String contentFix = str(feedbackMap, "contentFix");

                            return FeedbackDTO.builder()
                                    .question(question)
                                    .sttText(speechText)
                                    .mainPoint(reassemble(mainPointDiag, mainPointQuote, mainPointFix))
                                    .mainPointScore(mpScore)
                                    .mainPointQuote(mainPointQuote)
                                    .mainPointFix(mainPointFix)
                                    .expression(reassemble(expressionDiag, expressionQuote, expressionFix))
                                    .expressionScore(exScore)
                                    .expressionQuote(expressionQuote)
                                    .expressionFix(expressionFix)
                                    .accuracy(reassemble(accuracyDiag, accuracyQuote, accuracyFix))
                                    .accuracyScore(acScore)
                                    .accuracyQuote(accuracyQuote)
                                    .accuracyFix(accuracyFix)
                                    .fluency(computeFluencyText(speechText, fluencyScore))
                                    .fluencyScore(fluencyScore)
                                    .content(reassemble(contentDiag, contentQuote, contentFix))
                                    .contentScore(ctScore)
                                    .contentQuote(contentQuote)
                                    .contentFix(contentFix)
                                    .overall(computeOverallText(grade, mpScore, exScore, acScore, fluencyScore, ctScore))
                                    .overallGrade(grade)
                                    .improvements(reassemble(str(feedbackMap, "improvements"),
                                            str(feedbackMap, "improvementsQuote"), str(feedbackMap, "improvementsFix")))
                                    .modelAnswer(str(feedbackMap, "modelAnswer"))
                                    .modelAnswerComment(str(feedbackMap, "modelAnswerComment"))
                                    .tags(tags)
                                    .build();

                        } catch (Exception e) {
                            lastException = e;
                            lastWasRateLimited = isRateLimited(e);
                            log.warn("[Subtask-{}] 시도 {}/{} 실패{}: {}", idx, attempt + 1, maxAttempts,
                                    lastWasRateLimited ? " (429 rate limit)" : "", e.getMessage());
                        }
                    }

                    long subtaskMs = System.currentTimeMillis() - subtaskStart;
                    subtaskDurations.add(subtaskMs);
                    log.error("[Subtask-{}] 최종 실패 ({}회 시도): {}ms | {}",
                            idx, maxAttempts, subtaskMs, lastException.getMessage());
                    return FeedbackDTO.builder()
                            .question(question)
                            .failed(true)
                            .errorMessage(lastException.getMessage())
                            .build();
                }));
            }

            scope.joinUntil(Instant.now().plus(Duration.ofSeconds(90)));
            scope.throwIfFailed();

            List<FeedbackDTO> results = subtasks.stream()
                    .map(StructuredTaskScope.Subtask::get)
                    .toList();

            long parallelMs = System.currentTimeMillis() - start;
            long sequentialEstimateMs = subtaskDurations.stream().mapToLong(Long::longValue).sum();
            log.info("[Structured Concurrency 완료] 병렬: {}ms | 순차 예상: {}ms | 단축: {}ms ({}%)",
                    parallelMs, sequentialEstimateMs,
                    sequentialEstimateMs - parallelMs,
                    sequentialEstimateMs > 0 ? (sequentialEstimateMs - parallelMs) * 100 / sequentialEstimateMs : 0);
            return results;

        } catch (Exception e) {
            log.error("병렬 처리 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("피드백 분석 중 오류가 발생했습니다.", e);
        }
    }

    // STT/LLM 콜은 각각 다른 예외 스택(RestClient 직접 호출 vs Spring AI ChatModel 경유)으로 실패할 수 있어
    // 원인 체인을 끝까지 훑어 429(HttpClientErrorException.TooManyRequests)가 섞여 있는지 확인한다.
    private static boolean isRateLimited(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof HttpClientErrorException httpEx && httpEx.getStatusCode().value() == 429) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static FeedbackDTO selfIntroductionDto(QuestionDto question, String speechText) {
        return FeedbackDTO.builder()
                .question(question)
                .sttText(speechText)
                .overall("자기소개는 채점 대상이 아닙니다. 수고하셨어요!")
                .build();
    }

    // FU-02: 5단어 미만 "무응답" 조기 반환은 questionType을 보지 않고 mainPointScore=1을 항상 넣었다.
    // 정상 길이 응답은 이미 isRoleplayType()으로 TYPE_5~7의 MP를 null(평가 제외)로 처리하는데(SCORE-02),
    // 짧은 응답만 이 규칙을 우회해 롤플레이 무응답도 "핵심전달 1점" 표본으로 잘못 쌓였다.
    private static FeedbackDTO noResponseDto(QuestionDto question, String speechText) {
        Integer mainPointScore = isRoleplayType(question.getQuestionType()) ? null : 1;
        return FeedbackDTO.builder()
                .question(question)
                .sttText(speechText)
                .overall("응답이 감지되지 않았습니다.")
                .overallGrade("IL")
                .mainPointScore(mainPointScore).expressionScore(1).accuracyScore(1).fluencyScore(1).contentScore(1)
                .improvements("답변을 녹음해주세요.")
                .build();
    }

    private static String computeFluencyText(String text, int score) {
        int words = (text == null || text.isBlank()) ? 0 : text.trim().split("\\s+").length;
        return switch (score) {
            case 5 -> words + "단어. 발화량이 충분합니다.";
            case 4 -> words + "단어. 조금 더 말하면 만점이에요. (목표: 130단어+)";
            case 3 -> words + "단어. 발화량을 더 늘려보세요. (목표: 90단어+)";
            case 2 -> words + "단어. 더 길게 말하는 연습이 필요해요. (목표: 60단어+)";
            default -> words + "단어. 발화량이 많이 부족해요.";
        };
    }

    private static String computeOverallText(String grade, Integer... scores) {
        String[] labels = {"핵심전달", "표현력", "정확성", "발화량", "내용전개"};
        int minScore = 5;
        String weakest = null;
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] != null && scores[i] < minScore) {
                minScore = scores[i];
                weakest = labels[i];
            }
        }
        String base = grade + " 수준입니다.";
        return weakest != null ? base + " " + weakest + " 개선이 다음 목표예요." : base;
    }

    private static int computeFluencyScore(String text) {
        if (text == null || text.isBlank()) return 1;
        int words = text.trim().split("\\s+").length;
        if (words >= 130) return 5;
        if (words >= 90)  return 4;
        if (words >= 60)  return 3;
        if (words >= 30)  return 2;
        return 1;
    }

    private static boolean isRoleplayType(QuestionType type) {
        return type == QuestionType.TYPE_5 || type == QuestionType.TYPE_6 || type == QuestionType.TYPE_7;
    }

    private static String computeGrade(Integer... scores) {
        double avg = 0;
        int count = 0;
        for (Integer s : scores) {
            if (s != null) { avg += s; count++; }
        }
        if (count == 0) return "IM1";
        avg /= count;
        if (avg >= 4.5) return "AL";
        if (avg >= 3.8) return "IH";
        if (avg >= 3.2) return "IM3";
        if (avg >= 2.6) return "IM2";
        if (avg >= 2.0) return "IM1";
        return "IL";
    }

    // LLM이 진단/quote/fix를 분리해 반환해도, 화면(feedback.html)은 기존 "진단 + 예) ..." 한 덩어리 텍스트를 그대로 기대하므로 저장 직전에 재조합
    private static String reassemble(String diagnosis, String quote, String fix) {
        String base = diagnosis == null ? "" : diagnosis;
        if (quote != null && !quote.isBlank() && fix != null && !fix.isBlank()) {
            return base + "\n예) '" + quote + "' -> '" + fix + "'";
        }
        return base;
    }

    // 태깅 콜의 중첩 스키마({"mainPoint":[],"expression":{"vocab":[],"sentence":[],"imagery":[]},"accuracy":[],"content":[]})를
    // FeedbackTag row로 저장하기 쉽게 (category, tag) 평탄화. questionType은 mainPoint/content의
    // allowlist가 유형별로 달라 필요하다(FeedbackTagVocabulary 참고).
    private List<FeedbackTagDto> parseTags(String tagsJson, String questionType) {
        try {
            JsonNode root = objectMapper.readTree(tagsJson);
            List<FeedbackTagDto> result = new ArrayList<>();
            addTags(result, "mainPoint", root.get("mainPoint"), FeedbackTagVocabulary.mainPointOptions(questionType));
            JsonNode expression = root.get("expression");
            if (expression != null) {
                addTags(result, "vocab", expression.get("vocab"), FeedbackTagVocabulary.EXPRESSION_VOCAB);
                addTags(result, "sentence", expression.get("sentence"), FeedbackTagVocabulary.EXPRESSION_SENTENCE);
                addTags(result, "imagery", expression.get("imagery"), FeedbackTagVocabulary.EXPRESSION_IMAGERY);
            }
            addTags(result, "accuracy", root.get("accuracy"), FeedbackTagVocabulary.ACCURACY);
            addTags(result, "content", root.get("content"), FeedbackTagVocabulary.contentOptions(questionType));
            return result;
        } catch (Exception e) {
            log.warn("태그 파싱 실패, 빈 목록 반환: {}", e.getMessage());
            return List.of();
        }
    }

    // AI-01: LLM이 과도하게 긴 문자열을 태그로 반환해도 그대로 DB에 안 들어가게 길이를 제한하고,
    // 빈 문자열은 애초에 태그로서 의미가 없으므로 버린다.
    private static final int MAX_TAG_LENGTH = 60;
    // REVIEW-09: allowlist 검증만으로는 "유효한 태그를 비정상적으로 많이" 반환하는 경우(반복/환각)를
    // 못 막는다. 카테고리당 상한을 둬 한 카테고리가 통계를 도배하지 않도록 한다.
    private static final int MAX_TAGS_PER_CATEGORY = 5;

    // FU-06: 이전엔 allowlist를 통과한 동일 태그를 상한(5개)까지 그대로 중복 추가했다.
    // CoachingService가 태그 row 개수를 "발생 횟수"로 세므로, 답변 하나가 같은 태그를 5번
    // 반환하면 그것만으로 MIN_PATTERN_COUNT(3)를 채워 패턴처럼 보고되는 문제가 있었다.
    // LinkedHashSet으로 답변(카테고리) 하나당 같은 태그는 최초 1회만 남기고, 중복/blank/unknown은
    // 상한(distinct 개수 기준)을 소비하지 않는다.
    private static void addTags(List<FeedbackTagDto> result, String category, JsonNode arr,
                                 java.util.Set<String> allowlist) {
        if (arr == null || !arr.isArray()) return;
        java.util.Set<String> distinctTags = new java.util.LinkedHashSet<>();
        for (JsonNode n : arr) {
            if (distinctTags.size() >= MAX_TAGS_PER_CATEGORY) break;
            String tag = n.asText();
            if (tag == null || tag.isBlank()) continue;
            if (tag.length() > MAX_TAG_LENGTH) tag = tag.substring(0, MAX_TAG_LENGTH);
            // REVIEW-09: 프롬프트가 지정한 고정 어휘 밖의 태그(환각/오타/스키마 이탈)는 저장하지 않는다.
            if (!allowlist.contains(tag)) continue;
            distinctTags.add(tag); // 이미 있던 태그면 size가 늘지 않아 상한을 소비하지 않는다.
        }
        for (String tag : distinctTags) {
            result.add(new FeedbackTagDto(category, tag));
        }
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    // AI-01: LLM 응답도 신뢰 경계 밖 입력이다. 점수 필드가 없거나 파싱이 안 되거나 1~5 범위를
    // 벗어나면(예: score=99) 조용히 null/기본값으로 넘기지 않고 예외를 던져, 이미 이 서브태스크를
    // 감싸고 있는 재시도 루프가 "LLM 응답 품질 실패"로 취급해 재시도하도록 한다. 예전엔 score()가
    // null을 반환하면 이 값을 그대로 int로 언박싱하는 호출부에서 NPE가 났고, 또는 mainPointScore의
    // 경우 "롤플레이라 평가 제외"와 "파싱 실패"가 똑같이 null로 뭉쳐져 통계에서 구분이 안 됐다.
    private static int score(Map<String, Object> map, String key) {
        Object v = map.get(key);
        Integer parsed = null;
        if (v instanceof Integer i) {
            parsed = i;
        } else if (v != null) {
            try { parsed = Integer.parseInt(v.toString()); } catch (NumberFormatException ignored) { }
        }
        if (parsed == null || parsed < 1 || parsed > 5) {
            throw new IllegalStateException("AI 응답의 점수 필드가 유효하지 않습니다: " + key + "=" + v);
        }
        return parsed;
    }

}
