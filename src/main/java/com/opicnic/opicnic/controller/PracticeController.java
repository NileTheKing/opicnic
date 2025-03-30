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

    @GetMapping("/combo") // Change to GetMapping.  /combo?topic=xx&difficulty=xx&algorithm=xx
    public String startPractice(
            @RequestParam("topic") String topic,
            @RequestParam("difficulty") String difficulty,
            @RequestParam("algorithm") String algorithm,
            Model model) { // Add Model parameter
        log.info("combo controller");
        List<Question> questions = practiceService.getComboQuestions(topic, difficulty, algorithm);
        model.addAttribute("questions", questions); // Pass questions to the template
        return "/practice/question"; // Return the name of your Thymeleaf template (practice.html)
    }

    @PostMapping("/combo/feedback")
    @ResponseBody
    public List<Map<String, String>> getComboFeedback(
            @RequestParam("files") List<MultipartFile> files) throws IOException {
        return feedbackService.getComboFeedback(files);
    }

    }


