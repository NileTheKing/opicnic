package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.ExamSchedule;
import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.SurveyProfile.TargetGrade;
import com.opicnic.opicnic.repository.ExamScheduleRepository;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.service.ExamPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/exam")
@RequiredArgsConstructor
public class ExamController {

    private final MemberRepository memberRepository;
    private final FeedbackResultRepository feedbackResultRepository;
    private final ExamScheduleRepository examScheduleRepository;
    private final ExamPlanService examPlanService;

    @GetMapping
    public String examPage(@AuthenticationPrincipal OAuth2User oAuth2User, Model model) {
        Member member = resolveMember(oAuth2User);
        List<FeedbackResult> results = feedbackResultRepository.findByMemberIdOrderByCreatedAtDesc(member.getId());

        ExamPlanService.DiagnosisResult diagnosis = examPlanService.diagnose(results);
        model.addAttribute("diagnosis", diagnosis);
        model.addAttribute("targetGrades", TargetGrade.values());
        model.addAttribute("totalCount", results.size());

        Optional<ExamSchedule> schedule = examScheduleRepository.findTopByMemberIdOrderByCreatedAtDesc(member.getId());
        schedule.ifPresent(s -> {
            ExamPlanService.StudyPlan plan = examPlanService.buildPlan(diagnosis, s.getTargetGrade(), s.getExamDate());
            model.addAttribute("schedule", s);
            model.addAttribute("plan", plan);
        });

        return "exam/prep";
    }

    @PostMapping("/schedule")
    public String saveSchedule(
            @AuthenticationPrincipal OAuth2User oAuth2User,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate examDate,
            @RequestParam TargetGrade targetGrade) {
        Member member = resolveMember(oAuth2User);
        examScheduleRepository.save(ExamSchedule.builder()
                .member(member)
                .examDate(examDate)
                .targetGrade(targetGrade)
                .build());
        return "redirect:/exam";
    }

    private Member resolveMember(OAuth2User oAuth2User) {
        String provider = oAuth2User.getAttributes().get("provider").toString();
        return memberRepository.findByProviderAndProviderId(provider, oAuth2User.getName())
                .orElseThrow();
    }
}
