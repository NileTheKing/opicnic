package com.opicnic.opicnic.service;

import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {

    private final ComboPracticeService comboPracticeService;
    private final STTService sttService;
    private final GeminiService geminiService;

    public List<QuestionDto> getComboQuestions(String topic, String difficulty) {
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

            for (int i = 0; i < inputStreams.size(); i++) {
                final int idx = i;
                final InputStream is = inputStreams.get(i);
                final QuestionDto question = questions.get(i);

                subtasks.add(scope.fork(() -> {
                    log.info("[Subtask-{}] STT & LLM 처리 시작 (Thread: {})", idx, Thread.currentThread());
                    Exception lastException = null;
                    for (int attempt = 0; attempt <= 2; attempt++) {
                        try {
                            if (attempt > 0) {
                                log.warn("[Subtask-{}] 재시도 {}/2", idx, attempt);
                                Thread.sleep(1000L * attempt);
                            }
                            String speechText = sttService.sendStreamToStt(is, "audio_" + idx + ".webm");
                            var feedbackMap = geminiService.getOpicFeedback(speechText, question);

                            return FeedbackDTO.builder()
                                    .question(question)
                                    .sttText(speechText)
                                    .vocabulary(feedbackMap.get("vocabulary"))
                                    .grammar(feedbackMap.get("grammar"))
                                    .mainPoint(feedbackMap.get("mainPoint"))
                                    .fluency(feedbackMap.get("fluency"))
                                    .content(feedbackMap.get("content"))
                                    .overall(feedbackMap.get("overall"))
                                    .improvements(feedbackMap.get("improvements"))
                                    .build();
                        } catch (Exception e) {
                            lastException = e;
                        }
                    }
                    log.error("[Subtask-{}] 3회 시도 후 최종 실패: {}", idx, lastException.getMessage());
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

            log.info("[Structured Concurrency 완료] 소요 시간: {}ms", System.currentTimeMillis() - start);
            return results;

        } catch (Exception e) {
            log.error("병렬 처리 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("피드백 분석 중 오류가 발생했습니다.", e);
        }
    }
}
