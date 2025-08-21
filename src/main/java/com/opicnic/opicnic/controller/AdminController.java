package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.QuestionSet;
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

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
        model.addAttribute("difficulties", SurveyDifficulty.values());
        return "admin/question-set-form";
    }

    @PostMapping("/question-sets")
    public String createQuestionSet(@ModelAttribute QuestionSet questionSet) {
        questionSetRepository.save(questionSet);
        return "redirect:/admin/question-sets";
    }

    @PostMapping("/question-sets/{id}/delete")
    public String deleteQuestionSet(@PathVariable Long id) {
        // Note: @Where(clause = "deleted = false")가 적용되어 findById는 삭제되지 않은 것만 찾습니다.
        // 따라서 삭제된 것을 수정하거나 다시 삭제하려는 시도는 여기서 막힙니다.
        questionSetRepository.findById(id).ifPresent(questionSet -> {
            questionSet.setDeleted(true);
            questionSetRepository.save(questionSet);
        });
        return "redirect:/admin/question-sets";
    }

    @GetMapping("/question-sets/{id}/edit")
    public String showEditQuestionSetForm(@PathVariable Long id, Model model) {
        questionSetRepository.findById(id).ifPresent(questionSet -> {
            model.addAttribute("questionSet", questionSet);
            model.addAttribute("topics", SurveyTopic.values());
            model.addAttribute("difficulties", SurveyDifficulty.values());
        });
        return "admin/question-set-form";
    }

    @PostMapping("/question-sets/{id}/edit")
    public String updateQuestionSet(@PathVariable Long id, @ModelAttribute QuestionSet questionSetDetails) {
        questionSetRepository.findById(id).ifPresent(existingQuestionSet -> {
            existingQuestionSet.setName(questionSetDetails.getName());
            existingQuestionSet.setTopic(questionSetDetails.getTopic());
            existingQuestionSet.setDifficulty(questionSetDetails.getDifficulty());
            questionSetRepository.save(existingQuestionSet);
        });
        return "redirect:/admin/question-sets";
    }
}
