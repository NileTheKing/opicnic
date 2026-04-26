package com.opicnic.opicnic.service;

import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackServiceV2 {

    private final ComboPracticeService comboPracticeService;
    private final STTService sttService;
    private final GeminiService geminiService;
    private final ExecutorService taskExecutor;

    public List<QuestionDto> getComboQuestions(String topic, String difficulty) {
        return comboPracticeService.getComboQuestions(topic, difficulty);
    }

    /**
     * [Java 21 최적화] CompletableFuture를 제거하고 구조적 동기 스타일로 변경
     * 가상 스레드 환경에서는 복잡한 비동기 API보다 읽기 쉬운 동기 방식의 병렬 처리가 권장됩니다.
     */
    public List<FeedbackDTO> getComboFeedbackStreaming(
            List<InputStream> inputStreams, List<QuestionDto> questions) {

        log.info("[가상 스레드 병렬 처리] 피드백 분석 시작");
        long start = System.currentTimeMillis();

        // 1. 병렬로 실행할 작업(Task) 정의
        List<Callable<FeedbackDTO>> tasks = new ArrayList<>();

        for (int i = 0; i < inputStreams.size(); i++) {
            final int idx = i;
            final InputStream is = inputStreams.get(i);
            final QuestionDto question = questions.get(i % questions.size());

            tasks.add(() -> {
                log.info("[Task] STT & Gemini 처리 시작 (Thread: {})", Thread.currentThread());
                // 가상 스레드 위에서 블로킹 I/O 수행
                String speechText = sttService.sendStreamToStt(is, "audio_" + idx + ".wav");
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
            });
        }

        try {
            // 2. 모든 작업을 동시에 실행하고 완료될 때까지 대기 (InvokeAll)
            // 가상 스레드는 여기서 블로킹되어도 물리 스레드를 반납하므로 매우 효율적입니다.
            List<Future<FeedbackDTO>> futures = taskExecutor.invokeAll(tasks);

            List<FeedbackDTO> results = new ArrayList<>();
            for (Future<FeedbackDTO> future : futures) {
                results.add(future.get()); // 결과 취합
            }

            long end = System.currentTimeMillis();
            log.info("[병렬 처리 완료] 소요 시간: {}ms", (end - start));
            return results;

        } catch (Exception e) {
            log.error("병렬 처리 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("피드백 분석 중 오류가 발생했습니다.", e);
        }
    }
}
