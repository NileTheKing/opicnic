package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.NotificationSetting;
import com.opicnic.opicnic.domain.SurveyProfile;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.NotificationSettingRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class MyPageController {

    private final MemberRepository memberRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final SurveyProfileRepository surveyProfileRepository;

    @GetMapping("/mypage")
    public String showSettings(Model model, @AuthenticationPrincipal OAuth2User user) {
        String providerId = user.getName();
        String provider = user.getAttributes().get("provider").toString();

        Member member = memberRepository.findByProviderAndProviderId(provider, providerId)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자가 없습니다."));

        NotificationSetting setting = notificationSettingRepository.findByMember(member)
                .orElseGet(() -> {
                    NotificationSetting s = new NotificationSetting();
                    s.setMember(member);
                    return notificationSettingRepository.save(s);
                });

        SurveyProfile surveyProfile = surveyProfileRepository.findByMemberId(member.getId())
                .orElseGet(() -> SurveyProfile.builder().member(member).build());

        model.addAttribute("notificationSetting", setting);
        model.addAttribute("member", member);
        model.addAttribute("surveyProfile", surveyProfile);
        model.addAttribute("occupationTypes", SurveyProfile.OccupationType.values());
        model.addAttribute("residenceTypes", SurveyProfile.ResidenceType.values());
        model.addAttribute("topicGroups", buildTopicGroups());
        return "mypage/mypage";
    }

    @PostMapping("/mypage/settings")
    public String updateSettings(@ModelAttribute NotificationSetting settingForm,
                                 @AuthenticationPrincipal OAuth2User user) {
        String providerId = user.getName();
        Member member = memberRepository.findByProviderAndProviderId("kakao", providerId).orElseThrow();
        NotificationSetting setting = notificationSettingRepository.findByMember(member).orElseThrow();

        setting.setExamScheduleNotification(settingForm.isExamScheduleNotification());
        setting.setReviewNotification(settingForm.isReviewNotification());
        setting.setStudyBoardNotification(settingForm.isStudyBoardNotification());

        notificationSettingRepository.save(setting);
        return "redirect:/mypage";
    }

    @PostMapping("/mypage/survey")
    public String updateSurvey(
            @RequestParam(required = false) SurveyProfile.OccupationType occupationType,
            @RequestParam(required = false) SurveyProfile.ResidenceType residenceType,
            @RequestParam(value = "selectedTopics", required = false) List<SurveyTopic> selectedTopics,
            @AuthenticationPrincipal OAuth2User user) {

        String providerId = user.getName();
        String provider = user.getAttributes().get("provider").toString();
        Member member = memberRepository.findByProviderAndProviderId(provider, providerId).orElseThrow();

        SurveyProfile profile = surveyProfileRepository.findByMemberId(member.getId())
                .orElseGet(() -> SurveyProfile.builder().member(member).build());

        profile.setOccupationType(occupationType);
        profile.setResidenceType(residenceType);
        profile.getSelectedTopics().clear();
        if (selectedTopics != null) {
            profile.getSelectedTopics().addAll(selectedTopics);
        }

        surveyProfileRepository.save(profile);
        return "redirect:/mypage";
    }

    private Map<String, List<SurveyTopic>> buildTopicGroups() {
        Map<String, List<SurveyTopic>> groups = new LinkedHashMap<>();
        groups.put("여가 활동", List.of(
                SurveyTopic.MOVIE_WATCHING, SurveyTopic.MUSIC_LISTENING, SurveyTopic.TV_WATCHING,
                SurveyTopic.PERFORMANCE_WATCHING, SurveyTopic.SPORTS_WATCHING,
                SurveyTopic.ONLINE_GAMING, SurveyTopic.SOCIAL_MEDIA_USE, SurveyTopic.SHOPPING,
                SurveyTopic.MUSEUM_VISITING, SurveyTopic.COFFEE_SHOP_GOING
        ));
        groups.put("취미 / 관심사", List.of(
                SurveyTopic.READING, SurveyTopic.COOKING, SurveyTopic.PHOTOGRAPHY,
                SurveyTopic.INSTRUMENT_PLAYING, SurveyTopic.DRAWING_PAINTING,
                SurveyTopic.PET_RAISING, SurveyTopic.GARDENING, SurveyTopic.VOLUNTEERING,
                SurveyTopic.RECYCLING, SurveyTopic.TECHNOLOGY, SurveyTopic.CURRENT_AFFAIRS
        ));
        groups.put("운동", List.of(
                SurveyTopic.JOGGING, SurveyTopic.WALKING, SurveyTopic.SWIMMING,
                SurveyTopic.CYCLING, SurveyTopic.HIKING, SurveyTopic.FITNESS_GYM,
                SurveyTopic.YOGA, SurveyTopic.GOLF, SurveyTopic.TENNIS,
                SurveyTopic.SOCCER, SurveyTopic.BASKETBALL, SurveyTopic.DANCING
        ));
        groups.put("여행 / 휴가", List.of(
                SurveyTopic.TRAVEL, SurveyTopic.CAMPING, SurveyTopic.BEACH_GOING,
                SurveyTopic.PARK_GOING, SurveyTopic.STAYCATION
        ));
        return groups;
    }
}
