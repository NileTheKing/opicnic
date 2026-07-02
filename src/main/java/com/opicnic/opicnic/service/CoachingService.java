package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.CoachingReport;
import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.SurveyProfile;
import com.opicnic.opicnic.repository.CoachingReportRepository;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
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
    private final SurveyProfileRepository surveyProfileRepository;

    public CoachingReport generate(Member member) {
        List<FeedbackResult> results = feedbackResultRepository.findByMemberIdOrderByCreatedAtDesc(
                member.getId(), PageRequest.of(0, RECENT_RESULTS_LIMIT));
        String targetGrade = surveyProfileRepository.findByMemberId(member.getId())
                .map(SurveyProfile::getTargetGrade)
                .map(g -> g.label)
                .orElse("IH");
        String feedbackTexts = buildFeedbackTexts(results);
        String patterns = geminiService.extractCoachingPatterns(feedbackTexts);
        String prompt = buildPrompt(results, patterns);
        String content = geminiService.getCoachingReport(prompt, targetGrade);
        return coachingReportRepository.save(CoachingReport.builder()
                .member(member)
                .content(content)
                .basedOnCount(results.size())
                .build());
    }

    // Call 1 입력: 피드백 텍스트만 (LLM이 패턴을 추출)
    private String buildFeedbackTexts(List<FeedbackResult> results) {
        StringBuilder sb = new StringBuilder();
        List<FeedbackResult> recent = results.stream().limit(RECENT_TEXT_LIMIT).toList();
        for (int i = 0; i < recent.size(); i++) {
            FeedbackResult r = recent.get(i);
            sb.append("Q").append(i + 1);
            if (r.getQuestionType() != null) sb.append(" [").append(r.getQuestionType()).append("]");
            if (r.getSurveyTopicName() != null) sb.append(" · ").append(r.getSurveyTopicName());
            sb.append("\n");
            if (r.getMainPoint() != null && !r.getMainPoint().isBlank())
                sb.append("  mainPoint: ").append(r.getMainPoint()).append("\n");
            if (r.getContent() != null && !r.getContent().isBlank())
                sb.append("  content: ").append(r.getContent()).append("\n");
            if (r.getExpression() != null && !r.getExpression().isBlank())
                sb.append("  expression: ").append(r.getExpression()).append("\n");
            if (r.getAccuracy() != null && !r.getAccuracy().isBlank())
                sb.append("  accuracy: ").append(r.getAccuracy()).append("\n");
            if (r.getImprovements() != null && !r.getImprovements().isBlank())
                sb.append("  improvements: ").append(r.getImprovements()).append("\n");
        }
        return sb.toString();
    }

    // Call 2 입력: 점수 집계 + Call 1이 추출한 패턴
    private String buildPrompt(List<FeedbackResult> results, String extractedPatterns) {
        List<ExamPlanService.TypeStat> types = examPlanService.buildWeakTypes(results);

        StringBuilder sb = new StringBuilder();
        sb.append("총 연습 문항 수: ").append(results.size()).append("개\n\n");

        sb.append("【항목별 평균 점수 (5점 만점)】\n");
        sb.append("메인포인트: ").append(ExamPlanService.weightedAvg(results, FeedbackResult::getMainPointScore)).append("\n");
        sb.append("내용 구성: ").append(ExamPlanService.weightedAvg(results, FeedbackResult::getContentScore)).append("\n");
        sb.append("표현력: ").append(ExamPlanService.weightedAvg(results, FeedbackResult::getExpressionScore)).append("\n");
        sb.append("발화량: ").append(ExamPlanService.weightedAvg(results, FeedbackResult::getFluencyScore)).append("\n");
        sb.append("정확성: ").append(ExamPlanService.weightedAvg(results, FeedbackResult::getAccuracyScore)).append("\n\n");

        sb.append("【유형별 점수 (약한 순, 연습한 유형만)】\n");
        for (var t : types) {
            if (t.count() == 0) continue;
            sb.append(t.typeLabel()).append(": ").append(t.avgScore()).append("점 (").append(t.count()).append("회)\n");
        }
        sb.append("\n");

        sb.append("【추출된 반복 패턴 (피드백 분석 결과)】\n");
        sb.append(extractedPatterns).append("\n\n");

        sb.append("【개선 표현 예시 (advice 영어 예시 참조용)】\n");
        results.stream().limit(RECENT_TEXT_LIMIT).forEach(r -> {
            if (r.getImprovements() != null && !r.getImprovements().isBlank())
                sb.append(r.getImprovements()).append("\n");
        });

        return sb.toString();
    }
}
