package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.service.FeedbackServiceV2;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Controller
@RequestMapping("/practice/combo")
@RequiredArgsConstructor
@Slf4j
public class PracticeComboController {

    private final FeedbackServiceV2 feedbackService;

    @GetMapping
    public String startComboPractice(
            @RequestParam String topic,
            @RequestParam String difficulty,
            Model model) {
        log.info("콤보 연습 시작: topic={}, difficulty={}", topic, difficulty);
        List<QuestionDto> questions = feedbackService.getComboQuestions(topic, difficulty);
        model.addAttribute("questions", questions);
        return "/practice/practice";
    }

    /**
     * [최종 최적화 버전] 
     * CompletableFuture를 제거하고 가상 스레드 내부에서 동기식으로 결과를 반환합니다.
     * 스프링 MVC는 이 메서드를 가상 스레드에서 실행하므로, 여기서 블로킹되어도 전체 성능에 지장이 없습니다.
     */
    @PostMapping("/feedback")
    public String submitComboAnswers(HttpServletRequest request, Model model) {
        log.info("[최종 최적화] 피드백 요청 수신");

        try {
            Collection<Part> parts = request.getParts();
            List<InputStream> inputStreams = new ArrayList<>();
            List<QuestionDto> questionsFromClient = new ArrayList<>();

            for (Part part : parts) {
                if (part.getName().equals("files")) {
                    inputStreams.add(part.getInputStream());
                }
            }

            // 테스트 데이터 세팅
            if (questionsFromClient.isEmpty()) {
                questionsFromClient.add(new QuestionDto(1L, "Tell me about yourself.", "MOVIE_WATCHING", "LEVEL_3"));
            }

            // 가상 스레드 내부에서 병렬 처리 수행 (동기식 리턴)
            List<FeedbackDTO> feedbackList = feedbackService.getComboFeedbackStreaming(inputStreams, questionsFromClient);
            
            model.addAttribute("feedbackList", feedbackList);
            return "/practice/feedback";

        } catch (Exception e) {
            log.error("처리 중 오류 발생: {}", e.getMessage(), e);
            return "error";
        }
    }
}
