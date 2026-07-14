package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.QuestionSet;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

// 뷰 렌더링 전용. 질문 세트 생성/수정/삭제는 AdminQuestionSetApiController(/api/admin/question-sets)가 처리한다.
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final QuestionSetRepository questionSetRepository;

    @GetMapping
    public String adminHome() {
        return "admin/dashboard";
    }

    @GetMapping("/question-sets")
    public String listQuestionSets(Model model) {
        List<QuestionSet> questionSets = questionSetRepository.findAll();
        model.addAttribute("questionSets", questionSets);
        return "admin/question-sets";
    }

    @GetMapping("/question-sets/new")
    public String showNewQuestionSetForm(Model model) {
        model.addAttribute("questionSet", new QuestionSet());
        model.addAttribute("topics", SurveyTopic.values());
        return "admin/question-set-form";
    }

    @GetMapping("/question-sets/{id}/edit")
    public String showEditQuestionSetForm(@PathVariable Long id, Model model) {
        questionSetRepository.findById(id).ifPresent(questionSet -> {
            model.addAttribute("questionSet", questionSet);
            model.addAttribute("topics", SurveyTopic.values());
        });
        return "admin/question-set-form";
    }
}
