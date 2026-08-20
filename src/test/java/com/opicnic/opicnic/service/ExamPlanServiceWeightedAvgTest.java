package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.FeedbackResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// SCORE-01 회귀 테스트: results는 항상 최신순(index 0 = 최신)이므로 index가 작을수록
// 가중치가 커야 한다. 이전 버그는 반대 방향(가장 오래된 값에 가중치 1.0)이었다.
class ExamPlanServiceWeightedAvgTest {

    private FeedbackResult withMainPoint(int score) {
        return FeedbackResult.builder().mainPointScore(score).build();
    }

    @Test
    void recentHighScorePullsWeightedAverageAboveSimpleAverage() {
        // 최신=5, 과거=1 (5개, MIN_FOR_WEIGHTED=3 이상)
        List<FeedbackResult> results = new ArrayList<>();
        results.add(withMainPoint(5)); // 최신
        results.add(withMainPoint(1));
        results.add(withMainPoint(1));
        results.add(withMainPoint(1));
        results.add(withMainPoint(1)); // 가장 오래됨

        double weighted = ExamPlanService.weightedAvg(results, FeedbackResult::getMainPointScore);
        double simple = 9.0 / 5;

        assertThat(weighted).isGreaterThan(simple);
    }

    @Test
    void noSamplesReturnsNullNotZero() {
        // REVIEW-02: 표본이 없으면(예: 롤플레이만 연습해 mainPointScore가 전부 null) 0.0이 아니라
        // null이어야 한다 — 0.0은 "가장 낮은 점수"와 구분이 안 돼 최약점으로 잘못 뽑힌다.
        assertThat(ExamPlanService.weightedAvg(List.of(), FeedbackResult::getMainPointScore)).isNull();

        List<FeedbackResult> onlyRoleplay = List.of(
                FeedbackResult.builder().mainPointScore(null).build(),
                FeedbackResult.builder().mainPointScore(null).build()
        );
        assertThat(ExamPlanService.weightedAvg(onlyRoleplay, FeedbackResult::getMainPointScore)).isNull();
    }

    @Test
    void recentLowScorePullsWeightedAverageBelowSimpleAverage() {
        // 최신=1, 과거=5 (반대 fixture)
        List<FeedbackResult> results = new ArrayList<>();
        results.add(withMainPoint(1)); // 최신
        results.add(withMainPoint(5));
        results.add(withMainPoint(5));
        results.add(withMainPoint(5));
        results.add(withMainPoint(5)); // 가장 오래됨

        double weighted = ExamPlanService.weightedAvg(results, FeedbackResult::getMainPointScore);
        double simple = 21.0 / 5;

        assertThat(weighted).isLessThan(simple);
    }

    @Test
    void belowMinForWeightedUsesSimpleAverage() {
        // MIN_FOR_WEIGHTED=3 미만(1/2개)은 단순 평균. 0개는 noSamplesReturnsNullNotZero()에서 다룬다.
        assertThat(ExamPlanService.weightedAvg(List.of(withMainPoint(4)), FeedbackResult::getMainPointScore))
                .isEqualTo(4.0);
        assertThat(ExamPlanService.weightedAvg(List.of(withMainPoint(2), withMainPoint(4)), FeedbackResult::getMainPointScore))
                .isEqualTo(3.0);
    }
}
