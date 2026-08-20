package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.service.QuestionAssemblyService;
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
    private final SurveyProfileRepository surveyProfileRepository;
    private final Random random;

    @GetMapping("/practice/type")
    public String typePractice(
            @RequestParam String type,
            @AuthenticationPrincipal OAuth2User oAuth2User,
            Model model) {
        log.info("유형별 연습 시작: type={}", type);
        try {
            QuestionType questionType = QuestionType.valueOf(type);
            Long memberId = findMemberId(oAuth2User);

            List<SurveyTopic> myTopics = memberId == null
                    ? List.of()
                    : surveyProfileRepository.findByMemberId(memberId)
                            .map(profile -> profile.getSelectedTopics().stream()
                                    .filter(t -> t != SurveyTopic.NO_EXERCISE)
                                    .toList())
                            .orElse(List.of());
            if (myTopics.isEmpty()) {
                return "redirect:/?invalidPractice=true";
            }

            // REVIEW-07: findExistingTopics는 "이 topic에 QuestionSet이 하나라도 있는지"만 보므로,
            // 그 세트들이 전부 이 questionType을 갖지 않은 불완전한 세트여도 후보에 남는다.
            // 그런 topic이 뽑히면 assembleSingle()이 예외를 던지고, 다른 사용자 topic이 있어도
            // 그냥 "연습 불가"로 끝났다 — 이 type을 실제로 낼 수 있는 topic만 후보로 남긴다.
            List<SurveyTopic> existing = questionSetRepository.findExistingTopics(myTopics);
            List<SurveyTopic> available = myTopics.stream()
                    .filter(existing::contains)
                    .filter(t -> questionAssemblyService.hasQuestionType(t, questionType))
                    .toList();
            if (available.isEmpty()) {
                return "redirect:/?invalidPractice=true";
            }
            SurveyTopic topic = available.get(random.nextInt(available.size()));

            QuestionDto question = questionAssemblyService.assembleSingle(topic, questionType);
            PracticeAttempt attempt = practiceAttemptService.createAttempt(
                    List.of(question), memberId, PracticeMode.COMBO, null, null);

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
