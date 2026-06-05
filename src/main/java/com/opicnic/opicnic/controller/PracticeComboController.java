package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.TopicCatalog;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/practice/combo")
@RequiredArgsConstructor
@Slf4j
public class PracticeComboController {

    private final FeedbackService feedbackService;
    private final MemberRepository memberRepository;
    private final QuestionSetRepository questionSetRepository;
    private final TopicCatalog topicCatalog;
    private final PracticeAttemptService practiceAttemptService;

    @GetMapping
    public String startComboPractice(
            @RequestParam String topic,
            @RequestParam String difficulty,
            @AuthenticationPrincipal OAuth2User oAuth2User,
            Model model) {
        log.info("콤보 연습 시작: topic={}, difficulty={}", topic, difficulty);
        try {
            SurveyTopic surveyTopic = SurveyTopic.valueOf(topic);
            SurveyDifficulty.valueOf(difficulty);
            if (!topicCatalog.practiceTopics().contains(surveyTopic)
                    || !questionSetRepository.findExistingTopics(List.of(surveyTopic)).contains(surveyTopic)) {
                return "redirect:/?invalidPractice=true";
            }

            var combo = feedbackService.getComboQuestions(topic, difficulty);
            PracticeAttempt attempt = practiceAttemptService.createAttempt(
                    combo.questions(), findMemberId(oAuth2User), PracticeMode.COMBO,
                    combo.comboPatternKey(), combo.comboCategory());
            model.addAttribute("questions", combo.questions());
            model.addAttribute("attemptId", attempt.attemptId());
            return "practice/question";
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("콤보 연습 시작 불가: {}", e.getMessage());
            return "redirect:/?invalidPractice=true";
        }
    }

    private Long findMemberId(OAuth2User oAuth2User) {
        if (oAuth2User == null) return null;

        String provider = oAuth2User.getAttribute("provider");
        return memberRepository.findByProviderAndProviderId(provider, oAuth2User.getName())
                .map(Member::getId)
                .orElse(null);
    }
}
