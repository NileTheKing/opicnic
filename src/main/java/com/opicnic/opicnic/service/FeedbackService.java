package com.opicnic.opicnic.service;

import com.opicnic.opicnic.dto.ComboQuestionsResult;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {

    private final ComboPracticeService comboPracticeService;
    private final STTService sttService;
    private final GeminiService geminiService;

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

            List<Long> subtaskDurations = new java.util.concurrent.CopyOnWriteArrayList<>();

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

                    for (int attempt = 0; attempt < maxAttempts; attempt++) {
                        try {
                            if (attempt > 0) {
                                long delay = (1000L << (attempt - 1))
                                        + ThreadLocalRandom.current().nextLong(300);
                                log.warn("[Subtask-{}] 재시도 {}/{}, {}ms 대기", idx, attempt, maxAttempts - 1, delay);
                                Thread.sleep(delay);
                            }

                            String speechText = sttService.sendStreamToStt(
                                    new ByteArrayInputStream(audioBuffer), "audio_" + idx + ".webm");
                            if (speechText == null || speechText.trim().split("\\s+").length < 5) {
                                subtaskDurations.add(System.currentTimeMillis() - subtaskStart);
                                return noResponseDto(question, speechText);
                            }
                            var feedbackMap = geminiService.getOpicFeedback(speechText, question);

                            long subtaskMs = System.currentTimeMillis() - subtaskStart;
                            subtaskDurations.add(subtaskMs);
                            log.info("[Subtask-{}] 완료: {}ms{}", idx, subtaskMs,
                                    attempt > 0 ? " (재시도 " + attempt + "회)" : "");

                            return FeedbackDTO.builder()
                                    .question(question)
                                    .sttText(speechText)
                                    .vocabulary(str(feedbackMap, "vocabulary"))
                                    .vocabularyScore(score(feedbackMap, "vocabularyScore"))
                                    .grammar(str(feedbackMap, "grammar"))
                                    .grammarScore(score(feedbackMap, "grammarScore"))
                                    .mainPoint(str(feedbackMap, "mainPoint"))
                                    .mainPointScore(score(feedbackMap, "mainPointScore"))
                                    .fluency(str(feedbackMap, "fluency"))
                                    .fluencyScore(computeFluencyScore(speechText))
                                    .content(str(feedbackMap, "content"))
                                    .contentScore(score(feedbackMap, "contentScore"))
                                    .overall(str(feedbackMap, "overall"))
                                    .overallGrade(computeGrade(
                                            score(feedbackMap, "mainPointScore"),
                                            score(feedbackMap, "vocabularyScore"),
                                            score(feedbackMap, "grammarScore"),
                                            computeFluencyScore(speechText),
                                            score(feedbackMap, "contentScore")))
                                    .improvements(str(feedbackMap, "improvements"))
                                    .modelAnswer(str(feedbackMap, "modelAnswer"))
                                    .modelAnswerComment(str(feedbackMap, "modelAnswerComment"))
                                    .build();

                        } catch (Exception e) {
                            lastException = e;
                            log.warn("[Subtask-{}] 시도 {}/{} 실패: {}", idx, attempt + 1, maxAttempts, e.getMessage());
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

    private static FeedbackDTO noResponseDto(QuestionDto question, String speechText) {
        return FeedbackDTO.builder()
                .question(question)
                .sttText(speechText)
                .overall("응답이 감지되지 않았습니다.")
                .overallGrade("IL")
                .mainPointScore(1).vocabularyScore(1).grammarScore(1).fluencyScore(1).contentScore(1)
                .improvements("답변을 녹음해주세요.")
                .build();
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
