package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.CoachingReport;
import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.repository.CoachingReportRepository;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CoachingService {

    private static final int RECENT_RESULTS_LIMIT = 30;
    private static final int RECENT_TEXT_LIMIT = 10;

    private final GeminiService geminiService;
    private final FeedbackResultRepository feedbackResultRepository;
    private final CoachingReportRepository coachingReportRepository;
    private final ExamPlanService examPlanService;

    public CoachingReport generate(Member member) {
        List<FeedbackResult> results = feedbackResultRepository.findByMemberIdOrderByCreatedAtDesc(
                member.getId(), PageRequest.of(0, RECENT_RESULTS_LIMIT));
        String prompt = buildPrompt(results);
        String content = geminiService.getCoachingReport(prompt);
        return coachingReportRepository.save(CoachingReport.builder()
                .member(member)
                .content(content)
                .basedOnCount(results.size())
                .build());
    }

    private String buildPrompt(List<FeedbackResult> results) {
        List<ExamPlanService.TypeStat> types = examPlanService.buildWeakTypes(results);

        StringBuilder sb = new StringBuilder();
        sb.append("총 연습 문항 수: ").append(results.size()).append("개\n\n");

        sb.append("【항목별 평균 점수 (5점 만점)】\n");
        sb.append("메인포인트: ").append(ExamPlanService.weightedAvg(results, FeedbackResult::getMainPointScore)).append("\n");
        sb.append("내용 구성: ").append(ExamPlanService.weightedAvg(results, FeedbackResult::getContentScore)).append("\n");
        sb.append("어휘/묘사: ").append(ExamPlanService.weightedAvg(results, FeedbackResult::getVocabularyScore)).append("\n");
        sb.append("발화량: ").append(ExamPlanService.weightedAvg(results, FeedbackResult::getFluencyScore)).append("\n");
        sb.append("문법: ").append(ExamPlanService.weightedAvg(results, FeedbackResult::getGrammarScore)).append("\n\n");

        sb.append("【유형별 점수 (약한 순, 연습한 유형만)】\n");
        for (var t : types) {
            if (t.count() == 0) continue;
            sb.append(t.typeLabel()).append(": ").append(t.avgScore()).append("점 (").append(t.count()).append("회)\n");
        }
        sb.append("\n");

        sb.append("【최근 문항별 피드백 (최신 순)】\n");
        results.stream().limit(RECENT_TEXT_LIMIT).forEach(r -> {
            sb.append("[").append(r.getQuestionType()).append("]\n");
            if (r.getMainPoint()   != null) sb.append("  핵심전달: ").append(r.getMainPoint()).append("\n");
            if (r.getVocabulary()  != null) sb.append("  어휘/묘사: ").append(r.getVocabulary()).append("\n");
            if (r.getGrammar()     != null) sb.append("  문법: ").append(r.getGrammar()).append("\n");
            if (r.getContent()     != null) sb.append("  내용구성: ").append(r.getContent()).append("\n");
        });

        return sb.toString();
    }
}
