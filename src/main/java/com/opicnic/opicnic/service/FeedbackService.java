package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.Question;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {

    private final STTService sttService;
    private final GeminiService geminiService;
    private final ExecutorService taskExecutor;

    /*
     * STT 변환 및 Gemini 피드백을 병렬로 처리하는 메서드
     * @param files: 업로드된 오디오 파일 리스트
     * @param questions: 문제 리스트
     * @return: 각 파일에 대한 STT 결과와 Gemini 피드백을 포함한 리스트
     */
    public List<FeedbackDTO> getComboFeedbackParallel(List<MultipartFile> files, List<QuestionDto> questions) {
        long start = System.currentTimeMillis();
        log.info("[병렬 처리] 피드백 처리 시작");

        List<CompletableFuture<FeedbackDTO>> futures = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            QuestionDto question = questions.get(i);

            CompletableFuture<FeedbackDTO> future = CompletableFuture.supplyAsync(() -> {
                try {
                    String sttResult = sttService.sendAudioToStt(file);
                    Map<String, String> geminiFeedback = geminiService.getOpicFeedback(sttResult, question);

                    return FeedbackDTO.builder()
                            .question(question)
                            .sttText(sttResult)
                            .vocabulary(geminiFeedback.get("vocabulary"))
                            .grammar(geminiFeedback.get("grammar"))
                            .mainPoint(geminiFeedback.get("mainPoint"))
                            //.pronunciation(geminiFeedback.get("pronunciation"))
                            .fluency(geminiFeedback.get("fluency"))
                            .content(geminiFeedback.get("content"))
                            .overall(geminiFeedback.get("overall"))
                            .improvements(geminiFeedback.get("improvements"))
                            .build();
                } catch (Exception e) {
                    log.error("피드백 처리 중 오류 발생", e);
                    return FeedbackDTO.builder()
                            .question(question)
                            .sttText("오류")
                            .vocabulary("오류")
                            .grammar("오류")
                            //.pronunciation("오류")
                            .fluency("오류")
                            .content("오류")
                            .overall("오류")
                            .improvements("오류 발생: " + e.getMessage())
                            .build();
                }
            }, taskExecutor);

            futures.add(future);
        }

        List<FeedbackDTO> result = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        long end = System.currentTimeMillis();
        log.info("[병렬 처리] 총 소요 시간: {}ms", (end - start));

        return result;
    }

//    public List<FeedbackDTO> getComboFeedbackSequential(List<MultipartFile> files, List<Question> questions) {
//        long start = System.currentTimeMillis();
//        log.info("[순차 처리] 피드백 처리 시작");
//
//        List<FeedbackDTO> result = new ArrayList<>();
//
//        for (int i = 0; i < files.size(); i++) {
//            MultipartFile file = files.get(i);
//            Question question = questions.get(i);
//
//            try {
//                String sttResult = sttService.sendAudioToStt(file);
//                Map<String, String> geminiFeedback = geminiService.getOpicFeedback(sttResult, question);
//
//                FeedbackDTO dto = FeedbackDTO.builder()
//                        .question(question)
//                        .sttText(sttResult)
//                        .vocabulary(geminiFeedback.get("vocabulary"))
//                        .grammar(geminiFeedback.get("grammar"))
//                        .pronunciation(geminiFeedback.get("pronunciation"))
//                        .fluency(geminiFeedback.get("fluency"))
//                        .content(geminiFeedback.get("content"))
//                        .overall(geminiFeedback.get("overall"))
//                        .improvements(geminiFeedback.get("improvements"))
//                        .build();
//                result.add(dto);
//            } catch (Exception e) {
//                log.error("피드백 처리 중 오류 발생", e);
//                result.add(FeedbackDTO.builder()
//                        .question(question)
//                        .sttText("오류")
//                        .vocabulary("오류")
//                        .grammar("오류")
//                        .pronunciation("오류")
//                        .fluency("오류")
//                        .content("오류")
//                        .overall("오류")
//                        .improvements("오류 발생: " + e.getMessage())
//                        .build());
//            }
//        }
//
//        long end = System.currentTimeMillis();
//        log.info("[순차 처리] 총 소요 시간: {}ms", (end - start));
//
//        return result;
//    }
}