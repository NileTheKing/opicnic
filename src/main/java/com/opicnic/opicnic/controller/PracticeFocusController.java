package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.service.TopicCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class PracticeFocusController {

    private final MemberRepository memberRepository;
    private final SurveyProfileRepository surveyProfileRepository;
    private final QuestionSetRepository questionSetRepository;
    private final TopicCatalog topicCatalog;

    @GetMapping("/practice/focus")
    public String focusPage(@AuthenticationPrincipal OAuth2User oAuth2User, Model model) {
        String provider = oAuth2User.getAttribute("provider");
        memberRepository.findByProviderAndProviderId(provider, oAuth2User.getName())
                .ifPresent(member -> surveyProfileRepository.findByMemberId(member.getId())
                        .ifPresent(profile -> {
                            List<SurveyTopic> existingTopics = questionSetRepository
                                    .findExistingTopics(topicCatalog.practiceTopics());
                            List<SurveyTopic> userTopics = profile.getSelectedTopics().stream()
                                    .filter(t -> t != SurveyTopic.NO_EXERCISE)
                                    .filter(existingTopics::contains)
                                    .toList();
                            List<SurveyTopic> otherTopics = existingTopics.stream()
                                    .filter(t -> !userTopics.contains(t))
                                    .toList();
                            String difficulty = profile.getPreferredDifficulty() != null
                                    ? profile.getPreferredDifficulty().name() : "LEVEL_3";
                            model.addAttribute("userTopics", userTopics);
                            model.addAttribute("otherTopics", otherTopics);
                            model.addAttribute("difficulty", difficulty);
                        }));
        return "practice/focus";
    }
}
