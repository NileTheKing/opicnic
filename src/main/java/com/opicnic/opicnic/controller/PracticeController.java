package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Question;
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.PracticeService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
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
            Model model,
            HttpSession session) {
        log.info("combo controller");

        List<Question> questions = practiceService.getComboQuestions(topic, difficulty, algorithm);
        session.setAttribute("questions", questions); // 추가됨. 질문도 저장
        model.addAttribute("questions", questions);
        return "/practice/question";
    }

    @PostMapping("/combo/feedback")
    public String getComboFeedback(
            @RequestParam("files") List<MultipartFile> files,
            HttpSession session) throws IOException {
        log.info("feedback controller");

        List<Map<String, String>> feedbackList = feedbackService.getComboFeedback(files);
        session.setAttribute("feedbackList", feedbackList); // 피드백 리스트를 모델에 추가
        return "redirect:/practice/feedback";
    }

    @GetMapping("/feedback")
    public String showFeedback(HttpSession session, Model model) {
        List<Map<String, String>> feedbackList = (List<Map<String, String>>) session.getAttribute("feedbackList");
        List<Question> questions = (List<Question>) session.getAttribute("questions");// 질문도 가져옴
        if (feedbackList == null) {
            return "redirect:/practice/combo"; // 세션에 없으면 combo로 다시
        }

        model.addAttribute("feedbackList", feedbackList);
        model.addAttribute("questions", questions); // 질문도 모델에 추가
        session.removeAttribute("feedbackList"); // 1회성 출력 후 삭제
        return "/practice/feedback"; // 타임리프 페이지
    }

    }


