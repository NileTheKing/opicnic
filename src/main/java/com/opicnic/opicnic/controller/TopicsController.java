package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
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

    private static final SurveyDifficulty DEFAULT_DIFFICULTY = SurveyDifficulty.LEVEL_3;

    private static final Set<SurveyTopic> RECOMMENDED = Set.of(
            SurveyTopic.LIVING_WITH_FAMILY,
            SurveyTopic.MOVIE_WATCHING, SurveyTopic.TV_WATCHING, SurveyTopic.PERFORMANCE_WATCHING,
            SurveyTopic.PARK_GOING, SurveyTopic.BEACH_GOING, SurveyTopic.SPORTS_WATCHING,
            SurveyTopic.COFFEE_SHOP_GOING, SurveyTopic.SHOPPING,
            SurveyTopic.MUSIC_LISTENING, SurveyTopic.INSTRUMENT_PLAYING,
            SurveyTopic.READING, SurveyTopic.SINGING, SurveyTopic.COOKING,
            SurveyTopic.NO_EXERCISE, SurveyTopic.WALKING, SurveyTopic.JOGGING, SurveyTopic.FITNESS_GYM,
            SurveyTopic.STAYCATION, SurveyTopic.DOMESTIC_TRAVEL
    );

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

        Map<String, List<SurveyTopic>> topicGroups = buildTopicGroups();
        int topicCount = topicGroups.values().stream().mapToInt(List::size).sum();

        model.addAttribute("topicGroups", topicGroups);
        model.addAttribute("topicCount", topicCount);
        model.addAttribute("myTopics", myTopics);
        model.addAttribute("myTopicCount", myTopics.size());
        model.addAttribute("preferredDifficulty", preferredDifficulty);
        model.addAttribute("recommended", RECOMMENDED);
        model.addAttribute("warnTopics", WARN_TOPICS);
        model.addAttribute("hints", HINTS);
        return "practice/topics";
    }

    private Map<String, List<SurveyTopic>> buildTopicGroups() {
        Map<String, List<SurveyTopic>> groups = new LinkedHashMap<>();
        groups.put("거주 형태", List.of(SurveyTopic.LIVING_WITH_FAMILY));
        groups.put("여가 활동", List.of(
                SurveyTopic.MOVIE_WATCHING, SurveyTopic.TV_WATCHING, SurveyTopic.PERFORMANCE_WATCHING,
                SurveyTopic.PARK_GOING, SurveyTopic.BEACH_GOING,
                SurveyTopic.SPORTS_WATCHING, SurveyTopic.COFFEE_SHOP_GOING, SurveyTopic.SHOPPING
        ));
        groups.put("취미 / 관심사", List.of(
                SurveyTopic.MUSIC_LISTENING, SurveyTopic.INSTRUMENT_PLAYING,
                SurveyTopic.READING, SurveyTopic.SINGING, SurveyTopic.COOKING
        ));
        groups.put("운동", List.of(
                SurveyTopic.NO_EXERCISE, SurveyTopic.WALKING, SurveyTopic.JOGGING, SurveyTopic.FITNESS_GYM
        ));
        groups.put("여행 / 휴가", List.of(
                SurveyTopic.STAYCATION, SurveyTopic.DOMESTIC_TRAVEL
        ));
        return groups;
    }
}
