package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class AnalyticsController {

    private final FeedbackResultRepository feedbackResultRepository;
    private final MemberRepository memberRepository;

    private static final Map<String, String> SCORE_LABELS = Map.of(
            "vocabulary", "어휘력",
            "grammar", "문법",
            "mainPoint", "메인포인트",
            "fluency", "유창성",
            "content", "내용 충실도"
    );

    private static final Map<QuestionType, String> TYPE_LABELS = Map.ofEntries(
            Map.entry(QuestionType.TYPE_1, "유형 1 · 현재 상태 묘사"),
            Map.entry(QuestionType.TYPE_2, "유형 2 · 루틴/습관"),
            Map.entry(QuestionType.TYPE_3, "유형 3 · 최근/최초 경험"),
            Map.entry(QuestionType.TYPE_4, "유형 4 · 기억에 남는 경험"),
            Map.entry(QuestionType.TYPE_5, "롤플레이 · 도입 질문"),
            Map.entry(QuestionType.TYPE_6, "롤플레이 · 전화/정보요청"),
            Map.entry(QuestionType.TYPE_7, "롤플레이 · 문제 해결"),
            Map.entry(QuestionType.TYPE_8, "롤플레이 · 유사 경험"),
            Map.entry(QuestionType.TYPE_9, "유형 9 · 과거·현재 비교"),
            Map.entry(QuestionType.TYPE_10, "유형 10 · 사회 이슈")
    );

    public record ScoreStat(String key, String label, double avg, int pct) {}
    public record TopicStat(String topicName, String topicLabel, int count, double avgScore, int pct) {}
    public record TypeStat(String typeKey, String typeLabel, int count, double avgScore, int pct) {}

    @GetMapping("/analytics")
    public String analytics(@AuthenticationPrincipal OAuth2User user, Model model) {
        String providerId = user.getName();
        String provider = user.getAttributes().get("provider").toString();
        var member = memberRepository.findByProviderAndProviderId(provider, providerId).orElseThrow();

        List<FeedbackResult> results = feedbackResultRepository.findByMemberIdOrderByCreatedAtDesc(member.getId());
        model.addAttribute("totalCount", results.size());

        if (results.isEmpty()) {
            return "analytics/analytics";
        }

        List<ScoreStat> scores = buildScoreStats(results);
        double minAvg = scores.stream().mapToDouble(ScoreStat::avg).min().orElse(0.0);
        List<String> weakestKeys = scores.stream()
                .filter(s -> s.avg() == minAvg)
                .map(ScoreStat::key)
                .toList();
        String weakestLabel = scores.stream()
                .filter(s -> weakestKeys.contains(s.key()))
                .map(ScoreStat::label)
                .collect(Collectors.joining(", "));
        model.addAttribute("scores", scores);
        model.addAttribute("weakestKeys", weakestKeys);
        model.addAttribute("weakestLabel", weakestLabel);
        model.addAttribute("topicStats", buildTopicStats(results));
        model.addAttribute("typeStats", buildTypeStats(results));

        return "analytics/analytics";
    }

    private List<ScoreStat> buildScoreStats(List<FeedbackResult> results) {
        Map<String, OptionalDouble> avgs = Map.of(
                "vocabulary", results.stream().filter(r -> r.getVocabularyScore() != null)
                        .mapToInt(FeedbackResult::getVocabularyScore).average(),
                "grammar", results.stream().filter(r -> r.getGrammarScore() != null)
                        .mapToInt(FeedbackResult::getGrammarScore).average(),
                "mainPoint", results.stream().filter(r -> r.getMainPointScore() != null)
                        .mapToInt(FeedbackResult::getMainPointScore).average(),
                "fluency", results.stream().filter(r -> r.getFluencyScore() != null)
                        .mapToInt(FeedbackResult::getFluencyScore).average(),
                "content", results.stream().filter(r -> r.getContentScore() != null)
                        .mapToInt(FeedbackResult::getContentScore).average()
        );

        List<String> order = List.of("vocabulary", "grammar", "mainPoint", "fluency", "content");
        return order.stream().map(key -> {
            double avg = avgs.get(key).orElse(0.0);
            return new ScoreStat(key, SCORE_LABELS.get(key), Math.round(avg * 10.0) / 10.0, (int) (avg / 5.0 * 100));
        }).toList();
    }

    private List<TopicStat> buildTopicStats(List<FeedbackResult> results) {
        return results.stream()
                .filter(r -> r.getSurveyTopicName() != null)
                .collect(Collectors.groupingBy(FeedbackResult::getSurveyTopicName))
                .entrySet().stream()
                .map(e -> {
                    String name = e.getKey();
                    List<FeedbackResult> group = e.getValue();
                    String label = topicLabel(name);
                    double avg = group.stream()
                            .mapToDouble(this::questionAvgScore)
                            .average().orElse(0.0);
                    avg = Math.round(avg * 10.0) / 10.0;
                    return new TopicStat(name, label, group.size(), avg, (int) (avg / 5.0 * 100));
                })
                .sorted(Comparator.comparingInt(TopicStat::count).reversed())
                .toList();
    }

    private List<TypeStat> buildTypeStats(List<FeedbackResult> results) {
        Map<QuestionType, List<FeedbackResult>> grouped = results.stream()
                .filter(r -> r.getQuestionType() != null)
                .collect(Collectors.groupingBy(FeedbackResult::getQuestionType));

        return Arrays.stream(QuestionType.values())
                .map(type -> {
                    List<FeedbackResult> group = grouped.getOrDefault(type, List.of());
                    String label = TYPE_LABELS.getOrDefault(type, type.name());
                    if (group.isEmpty()) {
                        return new TypeStat(type.name(), label, 0, 0.0, 0);
                    }
                    double avg = group.stream().mapToDouble(this::questionAvgScore).average().orElse(0.0);
                    avg = Math.round(avg * 10.0) / 10.0;
                    return new TypeStat(type.name(), label, group.size(), avg, (int) (avg / 5.0 * 100));
                })
                .sorted(Comparator.comparingInt((TypeStat s) -> s.count() == 0 ? 1 : 0)
                        .thenComparingDouble(TypeStat::avgScore))
                .toList();
    }

    private double questionAvgScore(FeedbackResult r) {
        List<Integer> scores = new ArrayList<>();
        if (r.getVocabularyScore() != null) scores.add(r.getVocabularyScore());
        if (r.getGrammarScore() != null) scores.add(r.getGrammarScore());
        if (r.getMainPointScore() != null) scores.add(r.getMainPointScore());
        if (r.getFluencyScore() != null) scores.add(r.getFluencyScore());
        if (r.getContentScore() != null) scores.add(r.getContentScore());
        return scores.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }

    private String topicLabel(String topicName) {
        try {
            return SurveyTopic.valueOf(topicName).getLabel();
        } catch (IllegalArgumentException e) {
            return topicName;
        }
    }
}
