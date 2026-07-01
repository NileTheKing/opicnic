package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.service.ExamPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
    private final ExamPlanService examPlanService;

    @Value("${opicnic.coaching.min-count:3}")
    private int coachingMinCount;

    private static final LinkedHashMap<String, String> SCORE_LABELS = new LinkedHashMap<>();
    static {
        SCORE_LABELS.put("mainPoint",   "핵심전달");
        SCORE_LABELS.put("content",     "내용전개");
        SCORE_LABELS.put("expression",  "표현력");
        SCORE_LABELS.put("fluency",     "발화량");
        SCORE_LABELS.put("accuracy",    "정확성");
    }

    public record ScoreStat(String key, String label, double avg, int pct) {}

    @GetMapping("/analytics")
    public String analytics(@AuthenticationPrincipal OAuth2User user, Model model) {
        String provider = user.getAttributes().get("provider").toString();
        var member = memberRepository.findByProviderAndProviderId(provider, user.getName()).orElseThrow();

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
        model.addAttribute("typeStats", examPlanService.buildWeakTypes(results));
        model.addAttribute("comboStats", examPlanService.buildWeakCombos(results));
        model.addAttribute("coachingAvailable", results.size() >= coachingMinCount);

        return "analytics/analytics";
    }

    private List<ScoreStat> buildScoreStats(List<FeedbackResult> results) {
        return SCORE_LABELS.entrySet().stream().map(entry -> {
            String key = entry.getKey();
            double avg = ExamPlanService.weightedAvg(results, r -> switch (key) {
                case "mainPoint"  -> r.getMainPointScore();
                case "content"    -> r.getContentScore();
                case "expression" -> r.getExpressionScore();
                case "fluency"    -> r.getFluencyScore();
                case "accuracy"   -> r.getAccuracyScore();
                default -> null;
            });
            avg = Math.round(avg * 10.0) / 10.0;
            return new ScoreStat(key, entry.getValue(), avg, (int) (avg / 5.0 * 100));
        }).toList();
    }
}
