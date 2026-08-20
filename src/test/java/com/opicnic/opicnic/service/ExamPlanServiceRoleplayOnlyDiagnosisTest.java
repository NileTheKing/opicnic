package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.FeedbackResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// REVIEW-02 회귀 테스트: 사용자가 롤플레이(TYPE_5~7)만 연습했다면 mainPointScore 표본이
// 하나도 없다(SCORE-02로 null 저장). 이전엔 weightedAvg가 표본 없음을 0.0으로 반환해
// diagnose()의 overall 평균에 "핵심전달 0점"이 그대로 섞였다 — 실제로는 좋은 점수만
// 받았는데도 overall이 크게 낮아지고, scoreAvgs에도 0.0이 최약점 후보로 남았다.
class ExamPlanServiceRoleplayOnlyDiagnosisTest {

    private final ExamPlanService service = new ExamPlanService();

    private FeedbackResult roleplayResult(int ex, int ac, int fl, int ct) {
        return FeedbackResult.builder()
                .mainPointScore(null) // 롤플레이 MP 평가 제외
                .expressionScore(ex).accuracyScore(ac).fluencyScore(fl).contentScore(ct)
                .build();
    }

    @Test
    void mainPointWithNoSamplesIsExcludedFromOverallAverageAndScoreAvgs() {
        List<FeedbackResult> results = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            results.add(roleplayResult(5, 5, 5, 5));
        }

        ExamPlanService.DiagnosisResult diagnosis = service.diagnose(results);

        // 표본이 없는 "핵심전달"이 scoreAvgs에 0.0으로 끼어들면 안 된다.
        assertThat(diagnosis.scoreAvgs()).doesNotContainKey("핵심전달");
        // 모든 실제 표본이 5점이므로, 0.0이 섞이지 않았다면 overall도 5.0이어야 한다.
        assertThat(diagnosis.overallAvg()).isEqualTo(5.0);
    }

    @Test
    void mixedTypesStillIncludeMainPointWhenSamplesExist() {
        List<FeedbackResult> results = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            results.add(FeedbackResult.builder()
                    .mainPointScore(3)
                    .expressionScore(3).accuracyScore(3).fluencyScore(3).contentScore(3)
                    .build());
        }

        ExamPlanService.DiagnosisResult diagnosis = service.diagnose(results);

        assertThat(diagnosis.scoreAvgs()).containsKey("핵심전달");
        assertThat(diagnosis.overallAvg()).isEqualTo(3.0);
    }
}
