package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Question;
import com.opicnic.opicnic.dto.FeedbackDto;
import com.opicnic.opicnic.dto.QuestionWrapperDto;
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

@Controller
@RequestMapping("/practice")
@RequiredArgsConstructor
@Slf4j
class PracticeComboController {

    private final ComboPracticeService practiceService;
    private final FeedbackService feedbackService;

    /*
     * 콤보 연습 시작
     * 문제생성 후 문제화면으로 이동
     */
    @GetMapping("/combo")
    public String startComboPractice(
            @RequestParam("topic") String topic,
            @RequestParam("difficulty") String difficulty,
            @RequestParam("algorithm") String algorithm,
            Model model,
            HttpSession session) {

        //log.info("combo controller");

        List<Question> questions = practiceService.getComboQuestions(topic, difficulty, algorithm);
        model.addAttribute("questions", questions);  // 타임리프 question.html로 전달

        // 세션에 저장
        session.setAttribute("practiceQuestions", questions);

        return "/practice/question";
    }

    @PostMapping("/combo/feedback")
    public String submitComboAnswers (
            @RequestParam("files") List<MultipartFile> files,
            @ModelAttribute QuestionWrapperDto questionWrapperDto,  // wrapper로 받아야 함
            Model model
    ) throws IOException {
        List<Question> questions = questionWrapperDto.getQuestions();
        List<FeedbackDto> feedbackList = feedbackService.getComboFeedbackParallel(files, questions);
        model.addAttribute("feedbackList", feedbackList);
        return "/practice/feedback";
    }
}

