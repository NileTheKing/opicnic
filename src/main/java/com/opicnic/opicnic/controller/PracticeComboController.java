// src/main/java/com/opicnic/opicnic/controller/PracticeComboController.java

package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto; // QuestionDto 임포트
// import com.opicnic.opicnic.domain.Question; // 더 이상 이 엔티티는 직접 사용하지 않음
import com.opicnic.opicnic.dto.QuestionWrapperDTO; // QuestionWrapperDTO도 QuestionDto를 포함하도록 변경 필요
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.ComboPracticeService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Controller
@RequestMapping("/practice")
@RequiredArgsConstructor
@Slf4j
class PracticeComboController {

    private final ComboPracticeService practiceService;
    private final FeedbackService feedbackService;

    @GetMapping("/combo")
    public String startComboPractice(
            @RequestParam("topic") String topic,
            @RequestParam("difficulty") String difficulty,
            Model model,
            HttpSession session) {

        // ComboPracticeService에서 List<QuestionDto>를 반환하도록 변경했으므로, 여기도 QuestionDto로 받습니다.
        List<QuestionDto> questions = practiceService.getComboQuestions(topic, difficulty);
        model.addAttribute("questions", questions);

        session.setAttribute("practiceQuestions", questions);

        return "/practice/question";
    }

    @PostMapping("/combo/feedback")
    public CompletableFuture<String> submitComboAnswers (
            @RequestParam("files") List<MultipartFile> files,
            @ModelAttribute QuestionWrapperDTO questionWrapperDto,
            Model model
    ) {
        List<QuestionDto> questionsFromClient = questionWrapperDto.getQuestions();

        return feedbackService.getComboFeedbackParallel(files, questionsFromClient)
                .thenApply(feedbackList -> {
                    model.addAttribute("feedbackList", feedbackList);
                    return "/practice/feedback";
                });
    }
}