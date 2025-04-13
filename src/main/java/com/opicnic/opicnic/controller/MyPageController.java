package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.NotificationSetting;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class MyPageController {

    private final MemberRepository memberRepository;
    private final NotificationSettingRepository notificationSettingRepository;

    @GetMapping("/mypage")
    public String showSettings(Model model, @AuthenticationPrincipal OAuth2User user) {
        // 1. 사용자 정보 꺼내기
        String providerId = user.getName(); // CustomOAuth2UserService에서 nameAttributeKey로 지정한 값
        String provider = user.getAttributes().get("provider").toString();

        // 2. 멤버 조회
        Member member = memberRepository.findByProviderAndProviderId(provider, providerId)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자가 없습니다."));

        // 3. 알림 설정 조회 또는 기본값 생성
        NotificationSetting setting = notificationSettingRepository.findByMember(member)
                .orElseGet(() -> {
                    NotificationSetting s = new NotificationSetting();
                    s.setMember(member);
                    return notificationSettingRepository.save(s);
                });

        // 4. 모델에 담기
        model.addAttribute("notificationSetting", setting);
        model.addAttribute("member", member); // 필요하면 닉네임 등 출력 가능

        return "mypage/mypage"; // 앞에 슬래시 없이 쓰는 게 보통 convention
    }

    @PostMapping("/mypage/settings")
    public String updateSettings(@ModelAttribute NotificationSetting settingForm,
                                 @AuthenticationPrincipal OAuth2User user) {

        String providerId = user.getName(); // CustomOAuth2UserService에서 nameAttributeKey로 지정한 값

        Member member = memberRepository.findByProviderAndProviderId("kakao", providerId)
                .orElseThrow();
        NotificationSetting setting = notificationSettingRepository.findByMember(member)
                .orElseThrow();

        setting.setExamScheduleNotification(settingForm.isExamScheduleNotification());
        setting.setReviewNotification(settingForm.isReviewNotification());
        setting.setStudyBoardNotification(settingForm.isStudyBoardNotification());

        notificationSettingRepository.save(setting);
        return "redirect:/mypage";
    }



}
