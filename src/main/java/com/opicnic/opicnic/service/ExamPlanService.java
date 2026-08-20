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

        // REVIEW-02: 롤플레이만 연습한 사용자는 mainPointScore 표본이 하나도 없다(TYPE_5~7은
        // "평가 제외"로 null 저장 — SCORE-02). weightedAvg가 표본 없음을 0.0으로 반환하면
        // "핵심전달 0점"으로 오인되어 전체 평균을 깎고 항상 최약점으로 뽑힌다. null로 구분해
        // 표본이 없는 항목은 overall 평균과 scoreAvgs(최약점 판정용)에서 아예 제외한다.
        Map<String, Double> rawAvgs = new LinkedHashMap<>();
        rawAvgs.put("핵심전달", weightedAvg(results, r -> r.getMainPointScore()));
        rawAvgs.put("내용전개",  weightedAvg(results, r -> r.getContentScore()));
        rawAvgs.put("표현력",     weightedAvg(results, r -> r.getExpressionScore()));
        rawAvgs.put("발화량",     weightedAvg(results, r -> r.getFluencyScore()));
        rawAvgs.put("정확성",     weightedAvg(results, r -> r.getAccuracyScore()));

        Map<String, Double> avgs = rawAvgs.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));

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

    // 지수 감쇠 가중 평균. results는 최신순(index 0 = 최신) 정렬 가정 —
    // 따라서 index가 작을수록(최신일수록) 가중치가 커야 한다 (SCORE-01).
    // REVIEW-02: 표본이 하나도 없으면(예: 롤플레이만 연습해 mainPointScore가 전부 null) 0.0이 아니라
    // null을 반환한다. 0.0은 "실제로 낮은 점수"와 구분이 안 되어 전체 평균과 최약점 판정을 왜곡시켰다.
    public static Double weightedAvg(List<FeedbackResult> results, java.util.function.Function<FeedbackResult, Integer> getter) {
        List<Integer> values = results.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .toList();
        if (values.isEmpty()) return null;
        if (values.size() < MIN_FOR_WEIGHTED) {
            return values.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        }
        int n = values.size();
        double weightSum = 0, valueSum = 0;
        for (int i = 0; i < n; i++) {
            double w = Math.pow(DECAY_ALPHA, i);
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
            double w = Math.pow(DECAY_ALPHA, i);
            valueSum += w * perQuestion.get(i);
            weightSum += w;
        }
        return valueSum / weightSum;
    }

    private double questionAvg(FeedbackResult r) {
        List<Integer> scores = new ArrayList<>();
        if (r.getExpressionScore() != null) scores.add(r.getExpressionScore());
        if (r.getAccuracyScore() != null) scores.add(r.getAccuracyScore());
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

    public String typeLabel(QuestionType type) {
        if (type == null) {
            return "자기소개";
        }
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
