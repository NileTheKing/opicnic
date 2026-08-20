package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.service.CoachingService;
import com.opicnic.opicnic.service.ExamPlanService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// REVIEW-02 회귀 테스트: 롤플레이만 연습해 mainPointScore 표본이 하나도 없는 사용자는
// "핵심전달" 항목이 scores 목록/최약점 후보에서 아예 빠져야 한다. 이전엔 weightedAvg가
// 표본 없음을 0.0으로 반환해 항상 "핵심전달이 가장 낮아요"로 잘못 표시됐다.
class AnalyticsControllerRoleplayOnlyTest {

    private FeedbackResult roleplayResult() {
        return FeedbackResult.builder()
                .mainPointScore(null)
                .expressionScore(4).accuracyScore(4).fluencyScore(4).contentScore(4)
                .build();
    }

    @Test
    void mainPointExcludedFromScoresAndWeakestWhenNoSamples() {
        FeedbackResultRepository feedbackResultRepository = Mockito.mock(FeedbackResultRepository.class);
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        CoachingService coachingService = Mockito.mock(CoachingService.class);
        ExamPlanService examPlanService = new ExamPlanService();

        AnalyticsController controller = new AnalyticsController(
                feedbackResultRepository, memberRepository, examPlanService, coachingService);

        OAuth2User user = Mockito.mock(OAuth2User.class);
        when(user.getAttributes()).thenReturn(Map.of("provider", "kakao"));
        when(user.getName()).thenReturn("provider-id-1");

        Member member = Member.builder().id(1L).provider("kakao").providerId("provider-id-1").build();
        when(memberRepository.findByProviderAndProviderId("kakao", "provider-id-1")).thenReturn(Optional.of(member));

        List<FeedbackResult> results = new ArrayList<>();
        for (int i = 0; i < 5; i++) results.add(roleplayResult());
        when(feedbackResultRepository.findSummaryByMemberId(1L)).thenReturn(results);
        when(coachingService.buildTeaser(any())).thenReturn(null);

        Model model = new ExtendedModelMap();
        controller.analytics(user, model);

        @SuppressWarnings("unchecked")
        List<AnalyticsController.ScoreStat> scores =
                (List<AnalyticsController.ScoreStat>) model.getAttribute("scores");
        @SuppressWarnings("unchecked")
        List<String> weakestKeys = (List<String>) model.getAttribute("weakestKeys");

        assertThat(scores).extracting(AnalyticsController.ScoreStat::key).doesNotContain("mainPoint");
        assertThat(weakestKeys).doesNotContain("mainPoint");
    }
}
