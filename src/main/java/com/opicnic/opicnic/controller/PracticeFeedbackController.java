package com.opicnic.opicnic.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PracticeFeedbackController {

    @GetMapping("/practice/feedback/result")
    public String feedbackResult(HttpSession session, Model model) {
        Object feedbackList = session.getAttribute("feedbackList");
        if (feedbackList == null) {
            return "redirect:/";
        }

        model.addAttribute("feedbackList", feedbackList);
        session.removeAttribute("feedbackList");
        session.removeAttribute("practiceFeedbackResults");
        return "/practice/feedback";
    }
}
