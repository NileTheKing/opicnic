package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import com.opicnic.opicnic.service.QuestionAssemblyService;
import com.opicnic.opicnic.service.TopicCatalog;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Random;

@Controller
@RequiredArgsConstructor
@Slf4j
public class PracticeTypeController {

    private final QuestionAssemblyService questionAssemblyService;
    private final PracticeAttemptService practiceAttemptService;
    private final MemberRepository memberRepository;
    private final QuestionSetRepository questionSetRepository;
    private final TopicCatalog topicCatalog;
    private final Random random;

    @GetMapping("/practice/type")
    public String typePractice(
            @RequestParam String type,
            @AuthenticationPrincipal OAuth2User oAuth2User,
            Model model) {
        log.info("유형별 연습 시작: type={}", type);
        try {
            QuestionType questionType = QuestionType.valueOf(type);

            List<SurveyTopic> available = questionSetRepository.findExistingTopics(topicCatalog.practiceTopics());
            if (available.isEmpty()) {
                return "redirect:/?invalidPractice=true";
            }
            SurveyTopic topic = available.get(random.nextInt(available.size()));

            QuestionDto question = questionAssemblyService.assembleSingle(topic, questionType);
            PracticeAttempt attempt = practiceAttemptService.createAttempt(
                    List.of(question), findMemberId(oAuth2User), PracticeMode.COMBO, null, null);

            model.addAttribute("questions", List.of(question));
            model.addAttribute("attemptId", attempt.attemptId());
            return "practice/question";
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("유형별 연습 시작 불가: {}", e.getMessage());
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
