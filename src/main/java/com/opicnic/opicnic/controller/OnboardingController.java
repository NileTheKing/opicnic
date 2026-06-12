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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class OnboardingController {

    private final MemberRepository memberRepository;
    private final SurveyProfileRepository surveyProfileRepository;
    private final TopicCatalog topicCatalog;


    private static final Map<SurveyTopic, String> TOPIC_HINTS = Map.of(
            SurveyTopic.NO_EXERCISE, "운동 관련 문제는 출제되지 않아요 — 다른 주제 부담을 줄여줘요",
            SurveyTopic.STAYCATION, "비교적 쉬운 편이에요",
            SurveyTopic.DOMESTIC_TRAVEL, "여행 경험이 있으면 쉽게 답할 수 있어요",
            SurveyTopic.PARK_GOING, "롤플레이 유형 추가 준비 필요",
            SurveyTopic.WALKING, "롤플레이 유형 추가 준비 필요",
            SurveyTopic.INTERNATIONAL_TRAVEL, "롤플레이 유형 추가 준비 필요",
            SurveyTopic.CONCERT_WATCHING, "공연 보기와 별도 주제 — 콘서트 전용 문제 출제"
    );

    private static final Map<SurveyTopic, Integer> TOPIC_SET_COUNTS = Map.ofEntries(
            Map.entry(SurveyTopic.MOVIE_WATCHING, 1),
            Map.entry(SurveyTopic.TV_WATCHING, 1),
            Map.entry(SurveyTopic.PERFORMANCE_WATCHING, 1),
            Map.entry(SurveyTopic.CONCERT_WATCHING, 2),
            Map.entry(SurveyTopic.PARK_GOING, 2),
            Map.entry(SurveyTopic.BEACH_GOING, 1),
            Map.entry(SurveyTopic.SPORTS_WATCHING, 1),
            Map.entry(SurveyTopic.COFFEE_SHOP_GOING, 1),
            Map.entry(SurveyTopic.SHOPPING, 1),
            Map.entry(SurveyTopic.MUSIC_LISTENING, 1),
            Map.entry(SurveyTopic.INSTRUMENT_PLAYING, 1),
            Map.entry(SurveyTopic.READING, 1),
            Map.entry(SurveyTopic.SINGING, 1),
            Map.entry(SurveyTopic.COOKING, 1),
            Map.entry(SurveyTopic.NO_EXERCISE, 1),
            Map.entry(SurveyTopic.WALKING, 2),
            Map.entry(SurveyTopic.JOGGING, 1),
            Map.entry(SurveyTopic.FITNESS_GYM, 1),
            Map.entry(SurveyTopic.STAYCATION, 1),
            Map.entry(SurveyTopic.DOMESTIC_TRAVEL, 2),
            Map.entry(SurveyTopic.INTERNATIONAL_TRAVEL, 2)
    );


    @GetMapping("/onboarding")
    public String showOnboarding(
            @RequestParam(defaultValue = "false") boolean start,
            Model model,
            @AuthenticationPrincipal OAuth2User user) {
        String providerId = user.getName();
        String provider = user.getAttributes().get("provider").toString();
        Member member = memberRepository.findByProviderAndProviderId(provider, providerId).orElseThrow();

        if (surveyProfileRepository.findByMemberId(member.getId()).isPresent()) {
            return "redirect:/";
        }

        model.addAttribute("targetGrades", SurveyProfile.TargetGrade.values());
        model.addAttribute("showIntro", !start);
        return "onboarding/onboarding";
    }

    @GetMapping("/onboarding/topics")
    public String showTopics(
            @RequestParam(defaultValue = "NO_WORK_EXPERIENCE") String occupationType,
            @RequestParam(defaultValue = "WITH_FAMILY") String residenceType,
            @RequestParam(defaultValue = "IM2") String targetGrade,
            @RequestParam(defaultValue = "LEVEL_4") String preferredDifficulty,
            Model model,
            @AuthenticationPrincipal OAuth2User user) {
        String providerId = user.getName();
        String provider = user.getAttributes().get("provider").toString();
        Member member = memberRepository.findByProviderAndProviderId(provider, providerId).orElseThrow();

        if (surveyProfileRepository.findByMemberId(member.getId()).isPresent()) {
            return "redirect:/";
        }

        model.addAttribute("occupationType", occupationType);
        model.addAttribute("residenceType", residenceType);
        model.addAttribute("targetGrade", targetGrade);
        model.addAttribute("preferredDifficulty", preferredDifficulty);
        Map<String, Integer> setCounts = TOPIC_SET_COUNTS.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue));
        Map<String, String> hints = TOPIC_HINTS.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue));

        model.addAttribute("topicGroups", buildTopicGroups());
        model.addAttribute("topicHints", hints);
        model.addAttribute("topicSetCounts", setCounts);
        model.addAttribute("topicIcons", topicCatalog.topicIcons());
        return "onboarding/onboarding-topics";
    }

    @PostMapping("/onboarding")
    public String completeOnboarding(
            @RequestParam(required = false) SurveyProfile.OccupationType occupationType,
            @RequestParam(required = false) SurveyProfile.ResidenceType residenceType,
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
                .targetGrade(resolvedGrade)
                .preferredDifficulty(resolvedDifficulty)
                .build();

        if (selectedTopics != null) {
            profile.getSelectedTopics().addAll(selectedTopics);
        }
        // 거주 형태 주제가 누락된 경우 안전하게 자동 추가 (폼 미선택 방어)
        SurveyTopic residenceTopic = (residenceType == SurveyProfile.ResidenceType.ALONE)
                ? SurveyTopic.LIVING_ALONE : SurveyTopic.LIVING_WITH_FAMILY;
        profile.getSelectedTopics().add(residenceTopic);

        surveyProfileRepository.save(profile);
        return "redirect:/";
    }

    private Map<String, List<SurveyTopic>> buildTopicGroups() {
        Map<String, List<SurveyTopic>> groups = new LinkedHashMap<>();
        // 실제 OPIc 서베이 4번 항목 기준 (2개 이상)
        groups.put("여가 활동", List.of(
                SurveyTopic.MOVIE_WATCHING, SurveyTopic.TV_WATCHING, SurveyTopic.PERFORMANCE_WATCHING,
                SurveyTopic.CONCERT_WATCHING, SurveyTopic.PARK_GOING, SurveyTopic.BEACH_GOING,
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
                SurveyTopic.STAYCATION, SurveyTopic.DOMESTIC_TRAVEL, SurveyTopic.INTERNATIONAL_TRAVEL
        ));
        return groups;
    }
}
