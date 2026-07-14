package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
                            var feedbackMap = groqService.getOpicFeedback(speechText, question);

                            String mainPointDiag = str(feedbackMap, "mainPoint");
                            String expressionDiag = str(feedbackMap, "expression");
                            String accuracyDiag = str(feedbackMap, "accuracy");
                            String contentDiag = str(feedbackMap, "content");

                            String tagsJson = groqService.extractFeedbackTags(
                                    question.getQuestionType().name(),
                                    mainPointDiag, expressionDiag, accuracyDiag, contentDiag);
                            List<FeedbackTagDto> tags = parseTags(tagsJson);

                            long subtaskMs = System.currentTimeMillis() - subtaskStart;
                            subtaskDurations.add(subtaskMs);
                            log.info("[Subtask-{}] 완료: {}ms{}", idx, subtaskMs,
                                    attempt > 0 ? " (재시도 " + attempt + "회)" : "");

                            int fluencyScore = computeFluencyScore(speechText);
                            int mpScore    = score(feedbackMap, "mainPointScore");
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

    private static FeedbackDTO noResponseDto(QuestionDto question, String speechText) {
        return FeedbackDTO.builder()
                .question(question)
                .sttText(speechText)
                .overall("응답이 감지되지 않았습니다.")
                .overallGrade("IL")
                .mainPointScore(1).expressionScore(1).accuracyScore(1).fluencyScore(1).contentScore(1)
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
    // FeedbackTag row로 저장하기 쉽게 (category, tag) 평탄화
    private List<FeedbackTagDto> parseTags(String tagsJson) {
        try {
            JsonNode root = objectMapper.readTree(tagsJson);
            List<FeedbackTagDto> result = new ArrayList<>();
            addTags(result, "mainPoint", root.get("mainPoint"));
            JsonNode expression = root.get("expression");
            if (expression != null) {
                addTags(result, "vocab", expression.get("vocab"));
                addTags(result, "sentence", expression.get("sentence"));
                addTags(result, "imagery", expression.get("imagery"));
            }
            addTags(result, "accuracy", root.get("accuracy"));
            addTags(result, "content", root.get("content"));
            return result;
        } catch (Exception e) {
            log.warn("태그 파싱 실패, 빈 목록 반환: {}", e.getMessage());
            return List.of();
        }
    }

    private static void addTags(List<FeedbackTagDto> result, String category, JsonNode arr) {
        if (arr == null || !arr.isArray()) return;
        for (JsonNode n : arr) result.add(new FeedbackTagDto(category, n.asText()));
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private static Integer score(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Integer i) return i;
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return null; }
    }

}
