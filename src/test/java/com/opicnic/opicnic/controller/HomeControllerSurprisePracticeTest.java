package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.repository.ExamScheduleRepository;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.service.CoachingService;
import com.opicnic.opicnic.service.ExamPlanService;
import com.opicnic.opicnic.service.MockExamService;
import com.opicnic.opicnic.service.TopicCatalog;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// PC-02 / DOMAIN-01 회귀 테스트: "돌발로 하기"는 일반 배경설문 주제가 아니라
// TopicCatalog.surpriseTopics() 전용 풀에서만 후보를 뽑아야 한다.
class HomeControllerSurprisePracticeTest {

    @Test
    void surprisePracticeOnlyQueriesSurprisePool() {
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        SurveyProfileRepository surveyProfileRepository = Mockito.mock(SurveyProfileRepository.class);
        QuestionSetRepository questionSetRepository = Mockito.mock(QuestionSetRepository.class);
        MockExamService mockExamService = Mockito.mock(MockExamService.class);
        TopicCatalog topicCatalog = new TopicCatalog();
        PracticeAttemptService practiceAttemptService = Mockito.mock(PracticeAttemptService.class);
        FeedbackResultRepository feedbackResultRepository = Mockito.mock(FeedbackResultRepository.class);
        ExamScheduleRepository examScheduleRepository = Mockito.mock(ExamScheduleRepository.class);
        ExamPlanService examPlanService = new ExamPlanService();
        CoachingService coachingService = Mockito.mock(CoachingService.class);

        HomeController controller = new HomeController(
                memberRepository, surveyProfileRepository, questionSetRepository, mockExamService,
                topicCatalog, practiceAttemptService, new Random(), feedbackResultRepository,
                examScheduleRepository, examPlanService, coachingService);

        OAuth2User user = Mockito.mock(OAuth2User.class);
        when(user.getAttributes()).thenReturn(Map.of("provider", "kakao"));
        when(user.getName()).thenReturn("provider-id-1");
        when(memberRepository.findByProviderAndProviderId(any(), any())).thenReturn(java.util.Optional.empty());
        when(questionSetRepository.findExistingTopics(any())).thenReturn(List.of(SurveyTopic.BANK_VISIT));

        controller.surprisePractice(user);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SurveyTopic>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(questionSetRepository).findExistingTopics(captor.capture());

        List<SurveyTopic> queriedTopics = captor.getValue();
        assertThat(queriedTopics).isEqualTo(topicCatalog.surpriseTopics());
        assertThat(queriedTopics).doesNotContainAnyElementsOf(topicCatalog.practiceTopics().stream()
                .filter(t -> !topicCatalog.surpriseTopics().contains(t)).toList());
    }
}
