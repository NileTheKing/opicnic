package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.SurveyProfile.TargetGrade;
import com.opicnic.opicnic.domain.enums.QuestionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExamPlanService {

    private static final int MIN_RESULTS_FOR_DIAGNOSIS = 5;

    public record DiagnosisResult(
            TargetGrade estimatedGrade,
            double overallAvg,
            boolean sufficient,
            Map<String, Double> scoreAvgs,
            List<String> weakAreas,
            List<QuestionType> weakTypes
    ) {}

    public record StudyPlan(
            long daysLeft,
            int comboCount,
            int typeCount,
            int mockCount,
            List<QuestionType> focusTypes,
            List<String> focusAreas,
            String message
    ) {}

    public DiagnosisResult diagnose(List<FeedbackResult> results) {
        if (results.size() < MIN_RESULTS_FOR_DIAGNOSIS) {
            return new DiagnosisResult(null, 0, false, Map.of(), List.of(), List.of());
        }

        List<FeedbackResult> recent = results.stream().limit(10).toList();

        Map<String, Double> avgs = Map.of(
                "어휘력", avg(recent, r -> r.getVocabularyScore()),
                "문법", avg(recent, r -> r.getGrammarScore()),
                "메인포인트", avg(recent, r -> r.getMainPointScore()),
                "유창성", avg(recent, r -> r.getFluencyScore()),
                "내용충실도", avg(recent, r -> r.getContentScore())
        );

        double overall = avgs.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
        TargetGrade estimated = estimateGrade(overall);

        List<String> weakAreas = avgs.entrySet().stream()
                .filter(e -> e.getValue() < overall - 0.3)
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .limit(2)
                .toList();

        List<QuestionType> weakTypes = buildWeakTypes(recent);

        return new DiagnosisResult(estimated, Math.round(overall * 10.0) / 10.0, true, avgs, weakAreas, weakTypes);
    }

    public StudyPlan buildPlan(DiagnosisResult diagnosis, TargetGrade target, LocalDate examDate) {
        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), examDate);
        if (daysLeft <= 0) {
            return new StudyPlan(0, 0, 0, 0, List.of(), List.of(), "시험일이 이미 지났습니다.");
        }

        int gap = gradeGap(diagnosis.estimatedGrade(), target);
        int[] counts = weeklyCounts(daysLeft, gap);

        return new StudyPlan(
                daysLeft,
                counts[0],
                counts[1],
                counts[2],
                diagnosis.weakTypes().stream().limit(3).toList(),
                diagnosis.weakAreas(),
                buildMessage(daysLeft, gap, counts[0] + counts[1] + counts[2], target)
        );
    }

    private TargetGrade estimateGrade(double avg) {
        if (avg >= 4.3) return TargetGrade.AL;
        if (avg >= 3.8) return TargetGrade.IH;
        if (avg >= 3.3) return TargetGrade.IM3;
        if (avg >= 2.8) return TargetGrade.IM2;
        if (avg >= 2.3) return TargetGrade.IM1;
        if (avg >= 1.8) return TargetGrade.IL;
        return TargetGrade.NH;
    }

    private int gradeGap(TargetGrade current, TargetGrade target) {
        if (current == null) return 3;
        List<TargetGrade> order = List.of(
                TargetGrade.NH, TargetGrade.IL, TargetGrade.IM1,
                TargetGrade.IM2, TargetGrade.IM3, TargetGrade.IH, TargetGrade.AL
        );
        int cur = order.indexOf(current);
        int tgt = order.indexOf(target);
        return Math.max(0, tgt - cur);
    }

    // returns [comboCount, typeCount, mockCount]
    private int[] weeklyCounts(long daysLeft, int gap) {
        if (gap == 0) return new int[]{2, 1, 0};
        if (daysLeft <= 7)  return new int[]{3, 2, 1};
        if (daysLeft <= 14) return gap >= 2 ? new int[]{3, 2, 1} : new int[]{2, 2, 1};
        if (daysLeft <= 30) return gap >= 3 ? new int[]{3, 2, 1} : new int[]{2, 1, 1};
        return gap >= 3 ? new int[]{2, 2, 1} : new int[]{2, 1, 0};
    }

    private List<QuestionType> buildWeakTypes(List<FeedbackResult> results) {
        return results.stream()
                .filter(r -> r.getQuestionType() != null)
                .collect(Collectors.groupingBy(FeedbackResult::getQuestionType,
                        Collectors.averagingDouble(this::questionAvg)))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .limit(3)
                .toList();
    }

    private double questionAvg(FeedbackResult r) {
        List<Integer> scores = new ArrayList<>();
        if (r.getVocabularyScore() != null) scores.add(r.getVocabularyScore());
        if (r.getGrammarScore() != null) scores.add(r.getGrammarScore());
        if (r.getMainPointScore() != null) scores.add(r.getMainPointScore());
        if (r.getFluencyScore() != null) scores.add(r.getFluencyScore());
        if (r.getContentScore() != null) scores.add(r.getContentScore());
        return scores.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    private double avg(List<FeedbackResult> results, java.util.function.Function<FeedbackResult, Integer> getter) {
        return results.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average().orElse(0);
    }

    private String buildMessage(long daysLeft, int gap, int weeklyTarget, TargetGrade target) {
        if (gap == 0) return target.label + " 수준에 도달했습니다. 유지 연습을 권장합니다.";
        if (daysLeft <= 7 && gap >= 3) return "남은 기간이 짧고 목표까지 거리가 있습니다. 매일 집중 연습이 필요합니다.";
        return target.label + " 달성을 위해 주 " + weeklyTarget + "회 연습을 권장합니다.";
    }
}
