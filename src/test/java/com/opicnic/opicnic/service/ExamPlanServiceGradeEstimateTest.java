package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.SurveyProfile.TargetGrade;
import com.opicnic.opicnic.domain.enums.QuestionType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 예상 등급은 코칭 점수 평균이 아니라 답변 수준의 중앙값이고, IH 이상은 12번 롤플레이(TYPE_7),
// AL은 14·15번(TYPE_9/10)에서도 그 수준이 나와야 인정한다(DOMAIN.md "답변 하나로는 볼 수 없는 것")
class ExamPlanServiceGradeEstimateTest {

    private static FeedbackResult answer(QuestionType type, String grade) {
        return FeedbackResult.builder().questionType(type).overallGrade(grade).build();
    }

    private static List<FeedbackResult> answers(int n, QuestionType type, String grade) {
        List<FeedbackResult> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(answer(type, grade));
        return list;
    }

    @Test
    void lessThanFiveGradedAnswersIsNotEnough() {
        List<FeedbackResult> results = answers(4, QuestionType.TYPE_1, "IH");
        results.add(answer(null, "IH")); // 자기소개는 세지 않는다
        assertThat(ExamPlanService.estimateGrade(results)).isNull();
    }

    @Test
    void medianOfAnswerLevelsTakesLowerMiddle() {
        List<FeedbackResult> results = new ArrayList<>(answers(3, QuestionType.TYPE_1, "IM1"));
        results.addAll(answers(3, QuestionType.TYPE_2, "IM2"));
        assertThat(ExamPlanService.estimateGrade(results).grade()).isEqualTo(TargetGrade.IM1);
    }

    @Test
    void ihWithoutRoleplayKeepsGradeButAsksForCheck() {
        ExamPlanService.GradeEstimate e = ExamPlanService.estimateGrade(answers(5, QuestionType.TYPE_1, "IH"));
        assertThat(e.grade()).isEqualTo(TargetGrade.IH);
        assertThat(e.checkNeeded()).isEqualTo(ExamPlanService.CHECK_ROLEPLAY);
    }

    @Test
    void ihWithWeakRoleplayDropsToRoleplayLevel() {
        List<FeedbackResult> results = new ArrayList<>(answers(5, QuestionType.TYPE_1, "IH"));
        results.add(answer(QuestionType.TYPE_7, "IL"));
        ExamPlanService.GradeEstimate e = ExamPlanService.estimateGrade(results);
        assertThat(e.grade()).isEqualTo(TargetGrade.IM2); // 롤플레이가 아무리 낮아도 IM2 밑으로는 안 내린다
        assertThat(e.checkNeeded()).isNull();
    }

    @Test
    void alNeedsIssueQuestionsAtAl() {
        List<FeedbackResult> results = new ArrayList<>(answers(5, QuestionType.TYPE_1, "AL"));
        results.add(answer(QuestionType.TYPE_7, "IH"));
        assertThat(ExamPlanService.estimateGrade(results))
                .isEqualTo(new ExamPlanService.GradeEstimate(TargetGrade.AL, ExamPlanService.CHECK_ISSUE));

        results.add(answer(QuestionType.TYPE_10, "IH"));
        assertThat(ExamPlanService.estimateGrade(results))
                .isEqualTo(new ExamPlanService.GradeEstimate(TargetGrade.IH, null));

        results.add(answer(QuestionType.TYPE_9, "AL"));
        assertThat(ExamPlanService.estimateGrade(results))
                .isEqualTo(new ExamPlanService.GradeEstimate(TargetGrade.AL, null));
    }
}
