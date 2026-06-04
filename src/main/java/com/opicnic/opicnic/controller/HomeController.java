package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.SurveyProfile;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.service.MockExamService;
import com.opicnic.opicnic.service.TopicCatalog;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Random;

@Controller
@RequiredArgsConstructor
@Slf4j
public class HomeController {

    private final MemberRepository memberRepository;
    private final SurveyProfileRepository surveyProfileRepository;
    private final QuestionSetRepository questionSetRepository;
    private final MockExamService mockExamService;
    private final TopicCatalog topicCatalog;
    private final PracticeAttemptService practiceAttemptService;
    private final Random random;

    @GetMapping("/")
    public String home(@AuthenticationPrincipal OAuth2User user, Model model) {
        model.addAttribute("supportedTopicCount", topicCatalog.supportedTopics().size());
        if (user != null) {
            String provider = user.getAttributes().get("provider").toString();
            memberRepository.findByProviderAndProviderId(provider, user.getName()).ifPresent(member -> {
                surveyProfileRepository.findByMemberId(member.getId()).ifPresent(profile -> {
                    List<SurveyTopic> practiceTopics = profile.getSelectedTopics().stream()
                            .filter(t -> t != SurveyTopic.NO_EXERCISE)
                            .toList();
                    model.addAttribute("selectedTopics", practiceTopics);
                });
            });
        }
        return "home";
    }

    // 선택 주제 중 랜덤 콤보
    @GetMapping("/practice/random")
    public String randomPractice(@AuthenticationPrincipal OAuth2User user) {
        String provider = user.getAttributes().get("provider").toString();
        Member member = memberRepository.findByProviderAndProviderId(provider, user.getName()).orElseThrow();
        SurveyProfile profile = surveyProfileRepository.findByMemberId(member.getId())
                .orElseThrow(() -> new IllegalStateException("서베이 프로필이 없습니다."));

        List<SurveyTopic> existingTopics = questionSetRepository.findExistingTopics(topicCatalog.practiceTopics());
        List<SurveyTopic> topics = profile.getSelectedTopics().stream()
                .filter(t -> t != SurveyTopic.NO_EXERCISE)
                .filter(existingTopics::contains)
                .toList();

        if (topics.isEmpty()) return "redirect:/?noTopics=true";

        SurveyTopic topic = topics.get(random.nextInt(topics.size()));
        String difficulty = profile.getPreferredDifficulty() != null
                ? profile.getPreferredDifficulty().name() : "LEVEL_3";
        log.info("랜덤 연습: {} ({})", topic, difficulty);
        return "redirect:/practice/combo?topic=" + topic.name() + "&difficulty=" + difficulty;
    }

    // 돌발: 지원 주제 전체 중 랜덤
    @GetMapping("/practice/surprise")
    public String surprisePractice(@AuthenticationPrincipal OAuth2User user) {
        String provider = user.getAttributes().get("provider").toString();
        String difficulty = memberRepository.findByProviderAndProviderId(provider, user.getName())
                .flatMap(m -> surveyProfileRepository.findByMemberId(m.getId()))
                .map(p -> p.getPreferredDifficulty() != null ? p.getPreferredDifficulty().name() : "LEVEL_3")
                .orElse("LEVEL_3");
        List<SurveyTopic> topics = questionSetRepository.findExistingTopics(topicCatalog.practiceTopics());
        if (topics.isEmpty()) return "redirect:/?noTopics=true";

        SurveyTopic topic = topics.get(random.nextInt(topics.size()));
        log.info("돌발 연습: {} ({})", topic, difficulty);
        return "redirect:/practice/combo?topic=" + topic.name() + "&difficulty=" + difficulty;
    }

    @GetMapping("/practice/mock")
    public String mockExam(@AuthenticationPrincipal OAuth2User user, Model model) {
        String provider = user.getAttributes().get("provider").toString();
        Member member = memberRepository.findByProviderAndProviderId(provider, user.getName()).orElseThrow();
        SurveyProfile profile = surveyProfileRepository.findByMemberId(member.getId())
                .orElseThrow(() -> new IllegalStateException("서베이 프로필이 없습니다."));

        try {
            List<QuestionDto> questions = mockExamService.createMockExam(profile);
            PracticeAttempt attempt = practiceAttemptService.createAttempt(questions, member.getId(), PracticeMode.MOCK_EXAM);
            model.addAttribute("questions", questions);
            model.addAttribute("attemptId", attempt.attemptId());
        } catch (IllegalStateException e) {
            log.warn("모의고사 시작 불가: {}", e.getMessage());
            return "redirect:/?noTopics=true";
        }
        return "practice/question";
    }
}
