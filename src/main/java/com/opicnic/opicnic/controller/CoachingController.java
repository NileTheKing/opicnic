package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.CoachingReport;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.repository.CoachingReportRepository;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.service.CoachingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/analytics/coaching")
@RequiredArgsConstructor
@Slf4j
public class CoachingController {

    private final MemberRepository memberRepository;
    private final FeedbackResultRepository feedbackResultRepository;
    private final CoachingReportRepository coachingReportRepository;
    private final CoachingService coachingService;

    @Value("${opicnic.coaching.min-count:3}")
    private int coachingMinCount;

    @GetMapping
    public String coachingPage(@AuthenticationPrincipal OAuth2User oAuth2User, Model model) {
        Member member = resolveMember(oAuth2User);
        long totalCount = feedbackResultRepository.countByMemberId(member.getId());
        CoachingReport latestReport = coachingReportRepository
                .findTopByMemberIdOrderByCreatedAtDesc(member.getId()).orElse(null);

        model.addAttribute("totalCount", totalCount);
        model.addAttribute("coachingMinCount", coachingMinCount);
        model.addAttribute("canGenerate", totalCount >= coachingMinCount);
        model.addAttribute("latestReport", latestReport);
        model.addAttribute("gradeLabels", List.of("NH", "IL", "IM1", "IM2", "IM3", "IH", "AL"));
        model.addAttribute("currentGradeLabel", "IM3");
        model.addAttribute("targetGradeLabel", "IH");
        model.addAttribute("avgScore", "3.3");
        model.addAttribute("targetThreshold", "3.8");
        model.addAttribute("latestReportParsed", coachingService.parseReport(latestReport));
        model.addAttribute("reports",
                coachingReportRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()));
        return "analytics/coaching";
    }

    @GetMapping("/{id}")
    public String coachingDetail(@PathVariable Long id, @AuthenticationPrincipal OAuth2User oAuth2User, Model model) {
        Member member = resolveMember(oAuth2User);
        CoachingReport report = coachingReportRepository.findByIdAndMemberId(id, member.getId()).orElseThrow();

        model.addAttribute("report", report);
        model.addAttribute("gradeLabels", List.of("NH", "IL", "IM1", "IM2", "IM3", "IH", "AL"));
        model.addAttribute("currentGradeLabel", "IM3");
        model.addAttribute("targetGradeLabel", "IH");
        model.addAttribute("avgScore", "3.3");
        model.addAttribute("targetThreshold", "3.8");
        model.addAttribute("reportParsed", coachingService.parseReport(report));
        return "analytics/coaching-detail";
    }

    @PostMapping
    public String generate(@AuthenticationPrincipal OAuth2User oAuth2User) {
        Member member = resolveMember(oAuth2User);
        if (!canGenerate(member)) return "redirect:/analytics/coaching";
        coachingService.generate(member);
        return "redirect:/analytics/coaching";
    }

    private boolean canGenerate(Member member) {
        return feedbackResultRepository.countByMemberId(member.getId()) >= coachingMinCount;
    }

    private Member resolveMember(OAuth2User oAuth2User) {
        String provider = oAuth2User.getAttributes().get("provider").toString();
        return memberRepository.findByProviderAndProviderId(provider, oAuth2User.getName()).orElseThrow();
    }
}
