package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.service.OpicComboPatternProvider;
import com.opicnic.opicnic.service.TopicCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class PracticeFocusController {

    private static final SurveyDifficulty DEFAULT_DIFFICULTY = SurveyDifficulty.LEVEL_3;

    private final MemberRepository memberRepository;
    private final SurveyProfileRepository surveyProfileRepository;
    private final QuestionSetRepository questionSetRepository;
    private final TopicCatalog topicCatalog;
    private final OpicComboPatternProvider comboPatternProvider;

    @GetMapping("/practice/focus")
    public String focusPage(@AuthenticationPrincipal OAuth2User oAuth2User, Model model) {
        String provider = oAuth2User.getAttribute("provider");
        SurveyDifficulty resolvedDifficulty = memberRepository.findByProviderAndProviderId(provider, oAuth2User.getName())
                .flatMap(member -> surveyProfileRepository.findByMemberId(member.getId()))
                .map(profile -> {
                    List<SurveyTopic> existingTopics = questionSetRepository
                            .findExistingTopics(topicCatalog.practiceTopics());
                    List<SurveyTopic> userTopics = profile.getSelectedTopics().stream()
                            .filter(t -> t != SurveyTopic.NO_EXERCISE)
                            .filter(existingTopics::contains)
                            .toList();
                    List<SurveyTopic> otherTopics = existingTopics.stream()
                            .filter(t -> !userTopics.contains(t))
                            .toList();
                    SurveyDifficulty difficulty = profile.getPreferredDifficulty() != null
                            ? profile.getPreferredDifficulty() : DEFAULT_DIFFICULTY;
                    model.addAttribute("userTopics", userTopics);
                    model.addAttribute("otherTopics", otherTopics);
                    model.addAttribute("difficulty", difficulty.name());
                    return difficulty;
                })
                .orElse(DEFAULT_DIFFICULTY);

        // 콤보별 탭이 현재 난이도에 없는 category(C4/C5)를 눌리는 버튼으로 보여주지 않도록,
        // 실제 지원 category 집합을 계산해 화면에서 숨기거나 비활성화할 수 있게 넘긴다 (PC-04).
        Set<String> supportedCategories = comboPatternProvider.getPatterns(resolvedDifficulty).stream()
                .map(pattern -> pattern.category())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        model.addAttribute("supportedComboCategories", supportedCategories);

        return "practice/focus";
    }
}
