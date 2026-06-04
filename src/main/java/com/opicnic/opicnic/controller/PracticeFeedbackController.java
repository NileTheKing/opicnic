package com.opicnic.opicnic.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PracticeFeedbackController {

    private static final String SESSION_FEEDBACK_RESULTS = "feedbackResults";

    @GetMapping("/practice/feedback/result")
    public String feedbackResult(HttpSession session, Model model) {
        Object feedbackResults = session.getAttribute(SESSION_FEEDBACK_RESULTS);
        if (feedbackResults == null) {
            return "redirect:/";
        }

        model.addAttribute(SESSION_FEEDBACK_RESULTS, feedbackResults);
        session.removeAttribute(SESSION_FEEDBACK_RESULTS);
        return "practice/feedback";
    }
}
