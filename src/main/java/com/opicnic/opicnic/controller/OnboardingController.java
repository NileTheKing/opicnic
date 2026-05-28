package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.SurveyProfile;
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
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequiredArgsConstructor
public class OnboardingController {

    private final MemberRepository memberRepository;
    private final SurveyProfileRepository surveyProfileRepository;
    private final TopicCatalog topicCatalog;

    private static final Set<SurveyTopic> WARN_TOPICS = Set.of();

    // 추천 주제 = 노출하는 19개 전체 (모두 1세트 쉬운 주제)
    private static final Set<SurveyTopic> RECOMMENDED = Set.of(
            SurveyTopic.MOVIE_WATCHING, SurveyTopic.TV_WATCHING, SurveyTopic.PERFORMANCE_WATCHING,
            SurveyTopic.PARK_GOING, SurveyTopic.BEACH_GOING, SurveyTopic.SPORTS_WATCHING,
            SurveyTopic.COFFEE_SHOP_GOING, SurveyTopic.SHOPPING,
            SurveyTopic.MUSIC_LISTENING, SurveyTopic.INSTRUMENT_PLAYING,
            SurveyTopic.READING, SurveyTopic.SINGING, SurveyTopic.COOKING,
            SurveyTopic.NO_EXERCISE, SurveyTopic.WALKING, SurveyTopic.JOGGING, SurveyTopic.FITNESS_GYM,
            SurveyTopic.STAYCATION, SurveyTopic.DOMESTIC_TRAVEL
    );

    private static final Map<SurveyTopic, String> TOPIC_HINTS = Map.of(
            SurveyTopic.NO_EXERCISE, "운동 관련 문제는 출제되지 않아요 — 다른 주제 부담을 줄여줘요",
            SurveyTopic.STAYCATION, "비교적 쉬운 편이에요",
            SurveyTopic.DOMESTIC_TRAVEL, "여행 경험이 있으면 쉽게 답할 수 있어요"
    );


    @GetMapping("/onboarding")
    public String showOnboarding(Model model, @AuthenticationPrincipal OAuth2User user) {
        String providerId = user.getName();
        String provider = user.getAttributes().get("provider").toString();
        Member member = memberRepository.findByProviderAndProviderId(provider, providerId).orElseThrow();

        if (surveyProfileRepository.findByMemberId(member.getId()).isPresent()) {
            return "redirect:/";
        }

        model.addAttribute("occupationTypes", SurveyProfile.OccupationType.values());
        model.addAttribute("residenceTypes", SurveyProfile.ResidenceType.values());
        model.addAttribute("targetGrades", SurveyProfile.TargetGrade.values());
        model.addAttribute("topicGroups", buildTopicGroups());
        model.addAttribute("recommended", RECOMMENDED);
        model.addAttribute("warnTopics", WARN_TOPICS);
        model.addAttribute("topicHints", TOPIC_HINTS);
        model.addAttribute("topicIcons", topicCatalog.topicIcons());
        return "onboarding/onboarding";
    }

    @PostMapping("/onboarding")
    public String completeOnboarding(
            @RequestParam(required = false) SurveyProfile.OccupationType occupationType,
            @RequestParam(required = false) SurveyProfile.ResidenceType residenceType,
            @RequestParam(defaultValue = "false") boolean isStudent,
            @RequestParam(required = false) SurveyProfile.TargetGrade targetGrade,
            @RequestParam(required = false) SurveyDifficulty preferredDifficulty,
            @RequestParam(value = "selectedTopics", required = false) List<SurveyTopic> selectedTopics,
            @AuthenticationPrincipal OAuth2User user) {

        String providerId = user.getName();
        String provider = user.getAttributes().get("provider").toString();
        Member member = memberRepository.findByProviderAndProviderId(provider, providerId).orElseThrow();

        SurveyProfile.TargetGrade resolvedGrade = targetGrade != null ? targetGrade : SurveyProfile.TargetGrade.IM2;
        SurveyDifficulty resolvedDifficulty = preferredDifficulty != null ? preferredDifficulty : resolvedGrade.recommendedDifficulty;

        SurveyProfile profile = SurveyProfile.builder()
                .member(member)
                .occupationType(occupationType)
                .residenceType(residenceType)
                .isStudent(isStudent)
                .targetGrade(resolvedGrade)
                .preferredDifficulty(resolvedDifficulty)
                .build();

        if (selectedTopics != null) {
            profile.getSelectedTopics().addAll(selectedTopics);
        }
        // 거주 형태에 따라 거주 주제 자동 추가
        profile.getSelectedTopics().add(SurveyTopic.LIVING_WITH_FAMILY);

        surveyProfileRepository.save(profile);
        return "redirect:/";
    }

    private Map<String, List<SurveyTopic>> buildTopicGroups() {
        Map<String, List<SurveyTopic>> groups = new LinkedHashMap<>();
        // 실제 OPIc 서베이 4번 항목 기준 (2개 이상)
        groups.put("여가 활동", List.of(
                SurveyTopic.MOVIE_WATCHING, SurveyTopic.TV_WATCHING, SurveyTopic.PERFORMANCE_WATCHING,
                SurveyTopic.PARK_GOING, SurveyTopic.BEACH_GOING,
                SurveyTopic.SPORTS_WATCHING, SurveyTopic.COFFEE_SHOP_GOING, SurveyTopic.SHOPPING
        ));
        // 실제 OPIc 서베이 5번 항목 기준 (1개 이상)
        groups.put("취미 / 관심사", List.of(
                SurveyTopic.MUSIC_LISTENING, SurveyTopic.INSTRUMENT_PLAYING,
                SurveyTopic.READING, SurveyTopic.SINGING, SurveyTopic.COOKING
        ));
        // 실제 OPIc 서베이 6번 항목 기준 (1개 이상)
        groups.put("운동", List.of(
                SurveyTopic.NO_EXERCISE, SurveyTopic.WALKING, SurveyTopic.JOGGING, SurveyTopic.FITNESS_GYM
        ));
        // 실제 OPIc 서베이 7번 항목 기준 (1개 이상)
        groups.put("여행 / 휴가", List.of(
                SurveyTopic.STAYCATION, SurveyTopic.DOMESTIC_TRAVEL
        ));
        return groups;
    }
}
