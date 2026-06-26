package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.SurveyProfile.TargetGrade;
import com.opicnic.opicnic.domain.enums.QuestionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExamPlanServiceTest {

    private ExamPlanService service;

    @BeforeEach
    void setUp() {
        service = new ExamPlanService();
    }

    @Test
    @DisplayName("연습 기록 5개 미만이면 진단 불가")
    void diagnose_insufficient() {
        List<FeedbackResult> results = List.of(makeResult(3, 3, 3, 3, 3));
        ExamPlanService.DiagnosisResult result = service.diagnose(results);
        assertThat(result.sufficient()).isFalse();
        assertThat(result.estimatedGrade()).isNull();
    }

    @Test
    @DisplayName("연습 기록 5개 이상이면 등급 추정")
    void diagnose_sufficient() {
        List<FeedbackResult> results = makeResults(5, 4, 4, 4, 4, 4);
        ExamPlanService.DiagnosisResult result = service.diagnose(results);
        assertThat(result.sufficient()).isTrue();
        assertThat(result.estimatedGrade()).isNotNull();
        assertThat(result.overallAvg()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("평균 4.3 이상이면 AL 추정")
    void diagnose_highScore_estimatesAL() {
        List<FeedbackResult> results = makeResults(5, 5, 5, 4, 5, 4);
        ExamPlanService.DiagnosisResult result = service.diagnose(results);
        assertThat(result.estimatedGrade()).isEqualTo(TargetGrade.AL);
    }

    @Test
    @DisplayName("평균 낮으면 NH 추정")
    void diagnose_lowScore_estimatesNH() {
        List<FeedbackResult> results = makeResults(5, 1, 1, 1, 1, 1);
        ExamPlanService.DiagnosisResult result = service.diagnose(results);
        assertThat(result.estimatedGrade()).isEqualTo(TargetGrade.NH);
    }

    @Test
    @DisplayName("시험일 지났으면 빈 계획 반환")
    void buildPlan_pastExamDate() {
        ExamPlanService.DiagnosisResult diagnosis = service.diagnose(makeResults(5, 3, 3, 3, 3, 3));
        ExamPlanService.StudyPlan plan = service.buildPlan(diagnosis, TargetGrade.AL, LocalDate.now().minusDays(1));
        assertThat(plan.daysLeft()).isEqualTo(0);
        assertThat(plan.comboCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("간극 클수록 총 주당 횟수 높음")
    void buildPlan_largerGap_higherWeeklyTarget() {
        ExamPlanService.DiagnosisResult diagnosis = service.diagnose(makeResults(5, 2, 2, 2, 2, 2));
        LocalDate examDate = LocalDate.now().plusDays(30);

        ExamPlanService.StudyPlan planHighGap = service.buildPlan(diagnosis, TargetGrade.AL, examDate);
        ExamPlanService.StudyPlan planLowGap = service.buildPlan(diagnosis, TargetGrade.IM1, examDate);

        int totalHigh = planHighGap.comboCount() + planHighGap.typeCount() + planHighGap.mockCount();
        int totalLow = planLowGap.comboCount() + planLowGap.typeCount() + planLowGap.mockCount();
        assertThat(totalHigh).isGreaterThanOrEqualTo(totalLow);
    }

    @Test
    @DisplayName("취약 유형이 집중 유형으로 포함됨")
    void diagnose_weakTypesIncluded() {
        FeedbackResult low = makeResultWithType(1, 1, 1, 1, 1, QuestionType.TYPE_3);
        FeedbackResult high = makeResultWithType(5, 5, 5, 5, 5, QuestionType.TYPE_1);
        List<FeedbackResult> results = List.of(low, low, low, high, high, high);

        ExamPlanService.DiagnosisResult result = service.diagnose(results);
        assertThat(result.weakTypes()).contains(QuestionType.TYPE_3);
    }

    private List<FeedbackResult> makeResults(int count, int... scores) {
        return Collections.nCopies(count, makeResult(scores[0], scores[1], scores[2], scores[3], scores[4]));
    }

    private FeedbackResult makeResult(int v, int g, int m, int f, int c) {
        return FeedbackResult.builder()
                .vocabularyScore(v).grammarScore(g).mainPointScore(m)
                .fluencyScore(f).contentScore(c).build();
    }

    private FeedbackResult makeResultWithType(int v, int g, int m, int f, int c, QuestionType type) {
        return FeedbackResult.builder()
                .vocabularyScore(v).grammarScore(g).mainPointScore(m)
                .fluencyScore(f).contentScore(c).questionType(type).build();
    }
}
