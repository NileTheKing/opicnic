package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.ExamSchedule;
import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.SurveyProfile.TargetGrade;
import com.opicnic.opicnic.repository.ExamScheduleRepository;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.service.ExamPlanService;
import com.opicnic.opicnic.service.OpicComboPatternProvider;
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
import java.util.Set;

@Controller
@RequestMapping("/exam")
@RequiredArgsConstructor
public class ExamController {

    private static final SurveyDifficulty DEFAULT_DIFFICULTY = SurveyDifficulty.LEVEL_3;

    private final MemberRepository memberRepository;
    private final FeedbackResultRepository feedbackResultRepository;
    private final ExamScheduleRepository examScheduleRepository;
    private final SurveyProfileRepository surveyProfileRepository;
    private final ExamPlanService examPlanService;
    private final OpicComboPatternProvider comboPatternProvider;

    @GetMapping
    public String examPage(@AuthenticationPrincipal OAuth2User oAuth2User, Model model) {
        Member member = resolveMember(oAuth2User);
        List<FeedbackResult> results = feedbackResultRepository.findSummaryByMemberId(member.getId());

        ExamPlanService.DiagnosisResult diagnosis = examPlanService.diagnose(results);
        model.addAttribute("diagnosis", diagnosis);
        model.addAttribute("targetGrades", TargetGrade.values());
        model.addAttribute("totalCount", results.size());

        SurveyDifficulty difficulty = surveyProfileRepository.findByMemberId(member.getId())
                .map(p -> p.getPreferredDifficulty() != null ? p.getPreferredDifficulty() : DEFAULT_DIFFICULTY)
                .orElse(DEFAULT_DIFFICULTY);
        // 시험 계획의 "약한 콤보 추천"이 현재 난이도에서 실제로 시작할 수 없는 category(C4/C5)를
        // 추천 카드로 내보내지 않도록 필터링한다 (PC-04). 원래 순서(약한 순)는 그대로 유지한다.
        Set<String> supportedCategories = comboPatternProvider.getPatterns(difficulty).stream()
                .map(com.opicnic.opicnic.service.ComboPattern::category)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        Optional<ExamSchedule> schedule = examScheduleRepository.findTopByMemberIdOrderByCreatedAtDesc(member.getId());
        schedule.ifPresent(s -> {
            int dailyMinutes = s.getDailyMinutes() != null ? s.getDailyMinutes() : 60;
            int studyDaysPerWeek = s.getStudyDaysPerWeek() != null ? s.getStudyDaysPerWeek() : 5;
            ExamPlanService.StudyPlan plan = examPlanService.buildPlan(
                    diagnosis, s.getTargetGrade(), s.getExamDate(),
                    dailyMinutes, studyDaysPerWeek, results);
            model.addAttribute("schedule", s);
            model.addAttribute("plan", plan);
            model.addAttribute("startableWeakCombos", plan.weakCombos().stream()
                    .filter(combo -> supportedCategories.contains(combo.category()))
                    .toList());
        });

        return "exam/prep";
    }

    // API-02: 화면은 dailyMinutes={30,60,90,120}, studyDaysPerWeek={3,5,7}만 보여주지만
    // 서버는 그 약속을 강제하지 않아 임의 정수(음수, 0, 과도한 값)가 그대로 저장돼 학습 계획
    // 계산에 들어갈 수 있었다. 브라우저가 보여주는 선택지와 서버가 받아들이는 값의 범위를 맞춘다.
    private static final Set<Integer> ALLOWED_DAILY_MINUTES = Set.of(30, 60, 90, 120);
    private static final Set<Integer> ALLOWED_STUDY_DAYS_PER_WEEK = Set.of(3, 5, 7);

    @PostMapping("/schedule")
    public String saveSchedule(
            @AuthenticationPrincipal OAuth2User oAuth2User,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate examDate,
            @RequestParam TargetGrade targetGrade,
            @RequestParam(defaultValue = "60") int dailyMinutes,
            @RequestParam(defaultValue = "5") int studyDaysPerWeek) {
        if (!ALLOWED_DAILY_MINUTES.contains(dailyMinutes)
                || !ALLOWED_STUDY_DAYS_PER_WEEK.contains(studyDaysPerWeek)
                || examDate.isBefore(LocalDate.now())) {
            return "redirect:/exam?error=invalidSchedule";
        }
        Member member = resolveMember(oAuth2User);
        examScheduleRepository.save(ExamSchedule.builder()
                .member(member)
                .examDate(examDate)
                .targetGrade(targetGrade)
                .dailyMinutes(dailyMinutes)
                .studyDaysPerWeek(studyDaysPerWeek)
                .build());
        return "redirect:/exam";
    }

    private Member resolveMember(OAuth2User oAuth2User) {
        String provider = oAuth2User.getAttributes().get("provider").toString();
        return memberRepository.findByProviderAndProviderId(provider, oAuth2User.getName())
                .orElseThrow();
    }
}
