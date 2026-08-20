package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.CoachingReport;
import com.opicnic.opicnic.domain.ExamSchedule;
import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.repository.CoachingReportRepository;
import com.opicnic.opicnic.repository.ExamScheduleRepository;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.service.CoachingService;
import com.opicnic.opicnic.service.ExamPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

// B(오늘 할 일) — A(현황판)/C(설정) 재배치 설계는 학습관리 재설계 계획 참고.
// D-day/오늘 콤보 목표는 ExamPlanService.buildPlan()을 그대로 재사용하고, 이 컨트롤러는 그 결과를
// "오늘 할 일" 화면 형태로 재배치하는 역할만 한다 — 새 판단 로직은 회피 감지 하나뿐.
@Controller
@RequestMapping("/today")
@RequiredArgsConstructor
public class TodayController {

    private final MemberRepository memberRepository;
    private final FeedbackResultRepository feedbackResultRepository;
    private final ExamScheduleRepository examScheduleRepository;
    private final CoachingReportRepository coachingReportRepository;
    private final ExamPlanService examPlanService;
    private final CoachingService coachingService;

    public record AvoidedType(String typeKey, String typeLabel, long daysSince, boolean weak) {}

    @GetMapping
    public String today(@AuthenticationPrincipal OAuth2User oAuth2User, Model model) {
        Member member = resolveMember(oAuth2User);
        Optional<ExamSchedule> scheduleOpt = examScheduleRepository.findTopByMemberIdOrderByCreatedAtDesc(member.getId());
        model.addAttribute("hasSchedule", scheduleOpt.isPresent());
        if (scheduleOpt.isEmpty()) {
            return "today";
        }

        ExamSchedule schedule = scheduleOpt.get();
        List<FeedbackResult> results = feedbackResultRepository.findSummaryByMemberId(member.getId());
        ExamPlanService.DiagnosisResult diagnosis = examPlanService.diagnose(results);
        int dailyMinutes = schedule.getDailyMinutes() != null ? schedule.getDailyMinutes() : 60;
        int studyDaysPerWeek = schedule.getStudyDaysPerWeek() != null ? schedule.getStudyDaysPerWeek() : 5;
        ExamPlanService.StudyPlan plan = examPlanService.buildPlan(
                diagnosis, schedule.getTargetGrade(), schedule.getExamDate(),
                dailyMinutes, studyDaysPerWeek, results);

        List<FeedbackResult> todayResults = feedbackResultRepository
                .findByMemberIdAndCreatedAtAfter(member.getId(), LocalDate.now().atStartOfDay());
        long todayComboDone = todayResults.stream()
                .map(FeedbackResult::getAttemptId).filter(Objects::nonNull).distinct().count();

        int comboTarget = plan.dailyComboTarget();
        int progressPct = comboTarget > 0 ? (int) Math.min(100, todayComboDone * 100L / comboTarget) : 0;

        model.addAttribute("daysLeft", plan.daysLeft());
        model.addAttribute("todayComboDone", todayComboDone);
        model.addAttribute("todayComboTarget", comboTarget);
        model.addAttribute("todayProgressPct", progressPct);
        model.addAttribute("avoidedTypes", buildAvoidance(results, plan.weakTypes(), plan.daysLeft()));

        CoachingReport latestReport = coachingReportRepository
                .findTopByMemberIdOrderByCreatedAtDesc(member.getId()).orElse(null);
        if (latestReport != null) {
            Map<String, Object> parsed = coachingService.parseReport(latestReport);
            model.addAttribute("thisWeekTask", extractThisWeekTask(parsed.get("this_week")));
            model.addAttribute("thisWeekTaskDone", latestReport.isThisWeekTaskDone());
            model.addAttribute("latestReportId", latestReport.getId());
        }

        return "today";
    }

    @PostMapping("/task-done")
    public String toggleTaskDone(@RequestParam Long reportId,
                                  @RequestParam(defaultValue = "false") boolean done,
                                  @AuthenticationPrincipal OAuth2User oAuth2User) {
        Member member = resolveMember(oAuth2User);
        CoachingReport report = coachingReportRepository.findByIdAndMemberId(reportId, member.getId()).orElseThrow();
        report.setThisWeekTaskDone(done);
        coachingReportRepository.save(report);
        return "redirect:/today";
    }

    // 이번 주 과제는 신 스키마(practice/habit 객체)와 구 스키마(문자열) 둘 다 있을 수 있음 (coaching-report-card.html과 동일 처리)
    private String extractThisWeekTask(Object thisWeek) {
        if (thisWeek == null) return null;
        if (thisWeek instanceof Map<?, ?> map) {
            return String.valueOf(map.get("practice"));
        }
        return String.valueOf(thisWeek);
    }

    // 1단계: 유형별 마지막 연습일이 D-day 임계값 이상 지났으면 회피로 감지
    // 2단계: 그 중 약점 유형(weakTypes 상위 3, analytics.html의 강조 기준과 동일)이면 우선순위 높게 표시
    private List<AvoidedType> buildAvoidance(List<FeedbackResult> results,
                                              List<ExamPlanService.TypeStat> typeStats, long daysLeft) {
        int threshold = avoidanceThresholdDays(daysLeft);
        Set<String> weakTypeKeys = typeStats.stream()
                .filter(t -> t.count() > 0)
                .limit(3)
                .map(ExamPlanService.TypeStat::typeKey)
                .collect(Collectors.toSet());
        Map<String, String> typeLabels = typeStats.stream()
                .collect(Collectors.toMap(ExamPlanService.TypeStat::typeKey, ExamPlanService.TypeStat::typeLabel));

        Map<String, java.time.LocalDateTime> lastPracticeByType = new LinkedHashMap<>();
        for (FeedbackResult r : results) { // results는 최신순 정렬 가정 — putIfAbsent로 유형별 최초 등장(=최근값)만 남긴다
            if (r.getQuestionType() == null) continue;
            lastPracticeByType.putIfAbsent(r.getQuestionType().name(), r.getCreatedAt());
        }

        List<AvoidedType> avoided = new ArrayList<>();
        for (var e : lastPracticeByType.entrySet()) {
            long daysSince = ChronoUnit.DAYS.between(e.getValue().toLocalDate(), LocalDate.now());
            if (daysSince >= threshold) {
                avoided.add(new AvoidedType(e.getKey(), typeLabels.getOrDefault(e.getKey(), e.getKey()), daysSince,
                        weakTypeKeys.contains(e.getKey())));
            }
        }
        return avoided.stream()
                .sorted(Comparator.comparing(AvoidedType::weak).reversed()
                        .thenComparing(Comparator.comparingLong(AvoidedType::daysSince).reversed()))
                .toList();
    }

    private int avoidanceThresholdDays(long daysLeft) {
        if (daysLeft <= 4) return 2;
        if (daysLeft <= 7) return 3;
        if (daysLeft <= 14) return 5;
        return 7;
    }

    private Member resolveMember(OAuth2User oAuth2User) {
        String provider = oAuth2User.getAttributes().get("provider").toString();
        return memberRepository.findByProviderAndProviderId(provider, oAuth2User.getName()).orElseThrow();
    }
}
