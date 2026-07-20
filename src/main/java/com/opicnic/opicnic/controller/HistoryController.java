package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.service.ExamPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

// "기록"(A, /analytics)은 집계 통계만 보여주고 개별 기록 조회가 앱에 아예 없었던 걸 채우는 화면.
// CoachingController가 /analytics/coaching 하위 별도 컨트롤러인 것과 같은 패턴.
@Controller
@RequestMapping("/analytics/history")
@RequiredArgsConstructor
public class HistoryController {

    private final MemberRepository memberRepository;
    private final FeedbackResultRepository feedbackResultRepository;
    private final ExamPlanService examPlanService;

    @GetMapping
    public String list(@AuthenticationPrincipal OAuth2User oAuth2User, Model model) {
        Member member = resolveMember(oAuth2User);
        List<FeedbackResult> results = feedbackResultRepository
                .findByMemberIdOrderByCreatedAtDesc(member.getId(), PageRequest.of(0, 20));
        model.addAttribute("results", results);
        model.addAttribute("typeLabels", examPlanService);
        return "analytics/history";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal OAuth2User oAuth2User, Model model) {
        Member member = resolveMember(oAuth2User);
        FeedbackResult result = feedbackResultRepository.findByIdAndMemberId(id, member.getId()).orElseThrow();
        model.addAttribute("result", result);
        model.addAttribute("typeLabels", examPlanService);
        return "analytics/history-detail";
    }

    private Member resolveMember(OAuth2User oAuth2User) {
        String provider = oAuth2User.getAttribute("provider");
        return memberRepository.findByProviderAndProviderId(provider, oAuth2User.getName()).orElseThrow();
    }
}
