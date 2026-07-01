package com.opicnic.opicnic.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/analytics/coaching")
@RequiredArgsConstructor
@Slf4j
public class CoachingController {

    private final MemberRepository memberRepository;
    private final FeedbackResultRepository feedbackResultRepository;
    private final CoachingReportRepository coachingReportRepository;
    private final CoachingService coachingService;
    private final ObjectMapper objectMapper;

    @Value("${opicnic.coaching.min-count:3}")
    private int coachingMinCount;

    @GetMapping
    public String coachingPage(@AuthenticationPrincipal OAuth2User oAuth2User, Model model) {
        Member member = resolveMember(oAuth2User);
        long totalCount = feedbackResultRepository.countByMemberId(member.getId());
        CoachingReport latestReport = coachingReportRepository
                .findTopByMemberIdOrderByCreatedAtDesc(member.getId()).orElse(null);

        model.addAttribute("totalCount", totalCount);
        model.addAttribute("canGenerate", totalCount >= coachingMinCount);
        model.addAttribute("latestReport", latestReport);
        model.addAttribute("latestReportParsed", parseReport(latestReport));
        model.addAttribute("reports",
                coachingReportRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()));
        return "analytics/coaching";
    }

    @PostMapping
    public String generate(@AuthenticationPrincipal OAuth2User oAuth2User) {
        Member member = resolveMember(oAuth2User);
        if (!canGenerate(member)) return "redirect:/analytics/coaching";
        coachingService.generate(member);
        return "redirect:/analytics/coaching";
    }

    private Map<String, Object> parseReport(CoachingReport report) {
        if (report == null || report.getContent() == null) return Collections.emptyMap();
        try {
            return objectMapper.readValue(report.getContent(), new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("코칭 리포트 JSON 파싱 실패, 원문 사용: {}", e.getMessage());
            return Map.of("summary", report.getContent());
        }
    }

    private boolean canGenerate(Member member) {
        return feedbackResultRepository.countByMemberId(member.getId()) >= coachingMinCount;
    }

    private Member resolveMember(OAuth2User oAuth2User) {
        String provider = oAuth2User.getAttributes().get("provider").toString();
        return memberRepository.findByProviderAndProviderId(provider, oAuth2User.getName()).orElseThrow();
    }
}
