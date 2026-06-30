package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.SurveyProfile.TargetGrade;
import com.opicnic.opicnic.domain.enums.QuestionType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExamPlanService {

    static final int MINUTES_PER_COMBO = 15;
    private static final double DECAY_ALPHA = 0.85;
    private static final int MIN_FOR_WEIGHTED = 3;

    public static final Map<String, String> COMBO_LABELS = Map.of(
            "C1", "기본 콤보",
            "C2", "경험 심화 콤보",
            "C3", "롤플레이 콤보",
            "C4", "롤플레이 도입 콤보",
            "C5", "고난도 콤보"
    );

    public record DiagnosisResult(
            TargetGrade estimatedGrade,
            double overallAvg,
            boolean sufficient,
            Map<String, Double> scoreAvgs
    ) {}

    public record ComboStat(String category, String label, int count, double avgScore, int pct) {}

    public record TypeStat(String typeKey, String typeLabel, int count, double avgScore, int pct) {}

    public record StudyPlan(
            long daysLeft,
            int dailyComboTarget,
            int weeklyComboTarget,
            List<ComboStat> weakCombos,
            List<TypeStat> weakTypes,
            String message
    ) {
        public long daysLeft() { return daysLeft; }
    }

    public DiagnosisResult diagnose(List<FeedbackResult> results) {
        if (results.size() < 5) {
            return new DiagnosisResult(null, 0, false, Map.of());
        }

        Map<String, Double> avgs = Map.of(
                "핵심 전달", weightedAvg(results, r -> r.getMainPointScore()),
                "근거 전개",  weightedAvg(results, r -> r.getContentScore()),
                "표현력",     weightedAvg(results, r -> r.getVocabularyScore()),
                "유창성",     weightedAvg(results, r -> r.getFluencyScore()),
                "정확성",     weightedAvg(results, r -> r.getGrammarScore())
        );

        double overall = avgs.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return new DiagnosisResult(estimateGrade(overall), round1(overall), true, avgs);
    }

    public StudyPlan buildPlan(DiagnosisResult diagnosis, TargetGrade target,
                               LocalDate examDate, int dailyMinutes, int studyDaysPerWeek,
                               List<FeedbackResult> results) {
        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), examDate);
        if (daysLeft <= 0) {
            return new StudyPlan(0, 0, 0, List.of(), List.of(), "시험일이 이미 지났습니다.");
        }

        int dailyComboTarget = Math.max(1, dailyMinutes / MINUTES_PER_COMBO);
        int weeklyComboTarget = dailyComboTarget * studyDaysPerWeek;

        List<ComboStat> weakCombos = buildWeakCombos(results);
        List<TypeStat> weakTypes = buildWeakTypes(results);

        return new StudyPlan(daysLeft, dailyComboTarget, weeklyComboTarget,
                weakCombos, weakTypes, buildMessage(daysLeft, target));
    }

    public List<ComboStat> buildWeakCombos(List<FeedbackResult> results) {
        List<FeedbackResult> comboResults = results.stream()
                .filter(r -> r.getComboCategory() != null)
                .toList();

        Map<String, List<FeedbackResult>> grouped = comboResults.stream()
                .collect(Collectors.groupingBy(FeedbackResult::getComboCategory));

        return Arrays.asList("C1", "C2", "C3", "C4", "C5").stream()
                .map(cat -> {
                    List<FeedbackResult> group = grouped.getOrDefault(cat, List.of());
                    String label = COMBO_LABELS.getOrDefault(cat, cat);
                    if (group.isEmpty()) {
                        return new ComboStat(cat, label, 0, 0.0, 0);
                    }
                    double avg = round1(weightedAvgList(group));
                    return new ComboStat(cat, label, group.size(), avg, (int) (avg / 5.0 * 100));
                })
                .sorted(Comparator.comparingInt((ComboStat s) -> s.count() == 0 ? 0 : 1)
                        .thenComparingDouble(ComboStat::avgScore))
                .toList();
    }

    public List<TypeStat> buildWeakTypes(List<FeedbackResult> results) {
        Map<QuestionType, List<FeedbackResult>> grouped = results.stream()
                .filter(r -> r.getQuestionType() != null)
                .collect(Collectors.groupingBy(FeedbackResult::getQuestionType));

        return Arrays.stream(QuestionType.values())
                .map(type -> {
                    List<FeedbackResult> group = grouped.getOrDefault(type, List.of());
                    if (group.isEmpty()) {
                        return new TypeStat(type.name(), typeLabel(type), 0, 0.0, 0);
                    }
                    double avg = round1(weightedAvgList(group));
                    return new TypeStat(type.name(), typeLabel(type), group.size(), avg, (int) (avg / 5.0 * 100));
                })
                .sorted(Comparator.comparingInt((TypeStat s) -> s.count() == 0 ? 0 : 1)
                        .thenComparingDouble(TypeStat::avgScore))
                .toList();
    }

    // 지수 감쇠 가중 평균. results는 최신순 정렬 가정.
    public static double weightedAvg(List<FeedbackResult> results, java.util.function.Function<FeedbackResult, Integer> getter) {
        List<Integer> values = results.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .toList();
        if (values.isEmpty()) return 0.0;
        if (values.size() < MIN_FOR_WEIGHTED) {
            return values.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        }
        int n = values.size();
        double weightSum = 0, valueSum = 0;
        for (int i = 0; i < n; i++) {
            double w = Math.pow(DECAY_ALPHA, n - 1 - i);
            valueSum += w * values.get(i);
            weightSum += w;
        }
        return valueSum / weightSum;
    }

    private double weightedAvgList(List<FeedbackResult> results) {
        List<Double> perQuestion = results.stream()
                .map(this::questionAvg)
                .filter(v -> v > 0)
                .toList();
        if (perQuestion.isEmpty()) return 0.0;
        if (perQuestion.size() < MIN_FOR_WEIGHTED) {
            return perQuestion.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
        int n = perQuestion.size();
        double weightSum = 0, valueSum = 0;
        for (int i = 0; i < n; i++) {
            double w = Math.pow(DECAY_ALPHA, n - 1 - i);
            valueSum += w * perQuestion.get(i);
            weightSum += w;
        }
        return valueSum / weightSum;
    }

    private double questionAvg(FeedbackResult r) {
        List<Integer> scores = new ArrayList<>();
        if (r.getVocabularyScore() != null) scores.add(r.getVocabularyScore());
        if (r.getGrammarScore() != null) scores.add(r.getGrammarScore());
        if (r.getMainPointScore() != null) scores.add(r.getMainPointScore());
        if (r.getFluencyScore() != null) scores.add(r.getFluencyScore());
        if (r.getContentScore() != null) scores.add(r.getContentScore());
        return scores.stream().mapToInt(Integer::intValue).average().orElse(0.0);
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

    private String buildMessage(long daysLeft, TargetGrade target) {
        if (daysLeft <= 4)  return "지금은 약점만 집중하세요.";
        if (daysLeft <= 7)  return "지금 페이스면 핵심은 커버돼요.";
        if (daysLeft <= 14) return "콤보와 유형을 균형있게 연습하세요.";
        if (daysLeft <= 21) return "순서대로 꾸준히 진행하세요.";
        if (daysLeft <= 28) return "여유있게 전체를 커버할 수 있어요.";
        return "충분한 시간이 있어요. 반복 연습으로 실력을 쌓으세요.";
    }

    private String typeLabel(QuestionType type) {
        return switch (type) {
            case TYPE_1 -> "현재 상태 묘사";
            case TYPE_2 -> "루틴/습관";
            case TYPE_3 -> "최근/최초 경험";
            case TYPE_4 -> "기억에 남는 경험";
            case TYPE_5 -> "롤플레이 · 도입 질문";
            case TYPE_6 -> "롤플레이 · 전화/정보요청";
            case TYPE_7 -> "롤플레이 · 문제 해결";
            case TYPE_8 -> "롤플레이 · 유사 경험";
            case TYPE_9 -> "과거·현재 비교";
            case TYPE_10 -> "사회 이슈";
        };
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
