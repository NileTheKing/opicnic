package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.SurveyProfile;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.dto.ComboQuestionsResult;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.service.ComboPracticeService;
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.TopicCatalog;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Random;

@Controller
@RequestMapping("/practice/attempts")
@RequiredArgsConstructor
@Slf4j
public class PracticeComboController {

    private final FeedbackService feedbackService;
    private final ComboPracticeService comboPracticeService;
    private final MemberRepository memberRepository;
    private final QuestionSetRepository questionSetRepository;
    private final SurveyProfileRepository surveyProfileRepository;
    private final TopicCatalog topicCatalog;
    private final PracticeAttemptService practiceAttemptService;
    private final Random random;

    @PostMapping(params = {"mode=combo", "topic"})
    @ResponseStatus(HttpStatus.SEE_OTHER)
    public String startByTopic(
            @RequestParam String topic,
            @RequestParam String difficulty,
            @AuthenticationPrincipal OAuth2User oAuth2User) {
        try {
            SurveyTopic surveyTopic = SurveyTopic.valueOf(topic);
            if (!topicCatalog.practiceTopics().contains(surveyTopic)
                    || !questionSetRepository.findExistingTopics(List.of(surveyTopic)).contains(surveyTopic)) {
                return "redirect:/?invalidPractice=true";
            }
            ComboQuestionsResult combo = feedbackService.getComboQuestions(topic, difficulty);
            return startAttempt(combo, findMember(oAuth2User).getId());
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("콤보 연습 시작 불가: {}", e.getMessage());
            return "redirect:/?invalidPractice=true";
        }
    }

    @PostMapping(params = {"mode=combo", "category"})
    @ResponseStatus(HttpStatus.SEE_OTHER)
    public String startByCategory(
            @RequestParam String category,
            @AuthenticationPrincipal OAuth2User oAuth2User) {
        try {
            Member member = findMember(oAuth2User);
            SurveyProfile profile = surveyProfileRepository.findByMemberId(member.getId()).orElseThrow();
            List<SurveyTopic> userTopics = profile.getSelectedTopics().stream()
                    .filter(questionSetRepository.findExistingTopics(topicCatalog.practiceTopics())::contains)
                    .toList();
            if (userTopics.isEmpty()) return "redirect:/?invalidPractice=true";
            SurveyTopic picked = userTopics.get(random.nextInt(userTopics.size()));
            ComboQuestionsResult combo = comboPracticeService.getComboQuestionsByCategory(
                    picked, profile.getPreferredDifficulty(), category);
            return startAttempt(combo, member.getId());
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("카테고리 콤보 연습 시작 불가: {}", e.getMessage());
            return "redirect:/?invalidPractice=true";
        }
    }

    private String startAttempt(ComboQuestionsResult combo, Long memberId) {
        PracticeAttempt attempt = practiceAttemptService.createAttempt(
                combo.questions(), memberId, PracticeMode.COMBO,
                combo.comboPatternKey(), combo.comboCategory());
        return "redirect:/practice/attempts/" + attempt.attemptId();
    }

    private Member findMember(OAuth2User oAuth2User) {
        String provider = oAuth2User.getAttribute("provider");
        return memberRepository.findByProviderAndProviderId(provider, oAuth2User.getName()).orElseThrow();
    }
}
