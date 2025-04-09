package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Question;
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.PracticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Controller // Use Controller instead of RestController
@RequestMapping("/practice")
@RequiredArgsConstructor
@Slf4j
public class PracticeController {

    private final PracticeService practiceService;
    private final FeedbackService feedbackService;

    @GetMapping("/combo") //  /combo?topic=xx&difficulty=xx&algorithm=xx
    public String startPractice(
            @RequestParam("topic") String topic,
            @RequestParam("difficulty") String difficulty,
            @RequestParam("algorithm") String algorithm,
            Model model) {
        log.info("combo controller");
        List<Question> questions = practiceService.getComboQuestions(topic, difficulty, algorithm);
        model.addAttribute("questions", questions);
        return "/practice/question";
    }

    @PostMapping("/combo/feedback")
    public String getComboFeedback(
            @RequestParam("files") List<MultipartFile> files,
            Model model) throws IOException {
        log.info("feedback controller");

        List<Map<String, String>> feedbackList = feedbackService.getComboFeedback(files);
        model.addAttribute("feedbackList", feedbackList); // 피드백 리스트를 모델에 추가
        return "/practice/feedback";
    }

    }


