package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.Question;
import com.opicnic.opicnic.dto.FeedbackDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    public List<FeedbackDto> getComboFeedback(List<MultipartFile> files, List<Question> questions) {
        // 병렬 처리할 CompletableFuture 리스트
        List<CompletableFuture<FeedbackDto>> futures = new ArrayList<>();

        // 각 파일과 해당 문제에 대해 병렬로 처리
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            Question question = questions.get(i);

            // 비동기 처리
            CompletableFuture<FeedbackDto> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // STT 변환 결과
                    String sttResult = sttService.sendAudioToStt(file);
                    // Gemini API 호출하여 피드백 얻기
                    Map<String, String> geminiFeedback = geminiService.getOpicFeedback(sttResult, question);

                    // 피드백 DTO 생성
                    return FeedbackDto.builder()
                            .question(question)  // 문제 정보 포함
                            .sttText(sttResult)
                            .vocabulary(geminiFeedback.get("vocabulary"))
                            .grammar(geminiFeedback.get("grammar"))
                            .pronunciation(geminiFeedback.get("pronunciation"))
                            .fluency(geminiFeedback.get("fluency"))
                            .content(geminiFeedback.get("content"))
                            .overall(geminiFeedback.get("overall"))
                            .improvements(geminiFeedback.get("improvements"))
                            .build();

                } catch (Exception e) {
                    log.error("피드백 처리 중 오류 발생", e);
                    return FeedbackDto.builder()
                            .question(question)  // 오류 발생 시 문제 정보 포함
                            .sttText("오류")
                            .vocabulary("오류")
                            .grammar("오류")
                            .pronunciation("오류")
                            .fluency("오류")
                            .content("오류")
                            .overall("오류")
                            .improvements("오류 발생: " + e.getMessage())
                            .build();
                }
            }, taskExecutor); // 지정된 executor로 비동기 처리

            // 각 CompletableFuture를 리스트에 추가
            futures.add(future);
        }

        // 모든 CompletableFuture를 기다리고 결과를 리스트로 반환
        return futures.stream()
                .map(CompletableFuture::join)  // 각 비동기 작업 완료를 기다림
                .collect(Collectors.toList());  // 결과를 리스트로 수집
    }
}