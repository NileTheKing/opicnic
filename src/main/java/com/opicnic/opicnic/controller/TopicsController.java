package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.service.TopicCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;

@Controller
@RequiredArgsConstructor
public class TopicsController {

    private final MemberRepository memberRepository;
    private final SurveyProfileRepository surveyProfileRepository;
    private final TopicCatalog topicCatalog;

    private static final SurveyDifficulty DEFAULT_DIFFICULTY = SurveyDifficulty.LEVEL_3;

    private static final Set<SurveyTopic> WARN_TOPICS = Set.of();

    private static final Map<SurveyTopic, String> HINTS = Map.of(
            SurveyTopic.LIVING_WITH_FAMILY, "출제 비중 최고 — 필수 준비",
            SurveyTopic.NO_EXERCISE, "운동 문제 미출제 — 다른 주제 부담 줄여줘요",
            SurveyTopic.STAYCATION, "비교적 쉬운 편"
    );

    @GetMapping("/practice/topics")
    public String topicsPage(@AuthenticationPrincipal OAuth2User user, Model model) {
        Set<SurveyTopic> myTopics = Set.of();
        SurveyDifficulty preferredDifficulty = DEFAULT_DIFFICULTY;
        if (user != null) {
            String provider = user.getAttributes().get("provider").toString();
            var profile = memberRepository.findByProviderAndProviderId(provider, user.getName())
                    .flatMap(m -> surveyProfileRepository.findByMemberId(m.getId()));

            myTopics = profile
                    .map(p -> new HashSet<>(p.getSelectedTopics()))
                    .orElse(new HashSet<>());
            preferredDifficulty = profile
                    .map(p -> p.getPreferredDifficulty() != null ? p.getPreferredDifficulty() : DEFAULT_DIFFICULTY)
                    .orElse(DEFAULT_DIFFICULTY);
        }

        Map<String, List<SurveyTopic>> topicGroups = topicCatalog.groupedTopics();
        int topicCount = topicGroups.values().stream().mapToInt(List::size).sum();

        model.addAttribute("topicGroups", topicGroups);
        model.addAttribute("topicCount", topicCount);
        model.addAttribute("myTopics", myTopics);
        model.addAttribute("myTopicCount", myTopics.size());
        model.addAttribute("preferredDifficulty", preferredDifficulty);
        model.addAttribute("recommended", topicCatalog.recommendedTopics());
        model.addAttribute("warnTopics", WARN_TOPICS);
        model.addAttribute("hints", HINTS);
        model.addAttribute("topicIcons", topicCatalog.topicIcons());
        return "practice/topics";
    }
}
