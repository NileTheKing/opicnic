package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Member;
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
import com.opicnic.opicnic.service.job.ScoringJobService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.ui.ExtendedModelMap;

import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 게스트는 15문항 모의고사를 시작할 수 없다 — 하루 문항 한도를 한 번에 넘기고 LLM 무료 한도를 먹는다.
class HomeControllerGuestMockBlockTest {

    private final MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
    private final SurveyProfileRepository surveyProfileRepository = Mockito.mock(SurveyProfileRepository.class);
    private final MockExamService mockExamService = Mockito.mock(MockExamService.class);
    private final PracticeAttemptService attemptService = Mockito.mock(PracticeAttemptService.class);
    private final HomeController controller = new HomeController(
            memberRepository, surveyProfileRepository, Mockito.mock(QuestionSetRepository.class), mockExamService,
            new TopicCatalog(), attemptService, new Random(), Mockito.mock(FeedbackResultRepository.class),
            Mockito.mock(ExamScheduleRepository.class), new ExamPlanService(), Mockito.mock(CoachingService.class),
            Mockito.mock(ScoringJobService.class));

    private OAuth2User user(String provider) {
        OAuth2User user = Mockito.mock(OAuth2User.class);
        when(user.getAttributes()).thenReturn(Map.of("provider", provider));
        when(user.getName()).thenReturn("pid");
        return user;
    }

    @Test
    void 게스트가_모의고사에_들어오면_안내와_함께_홈으로_돌려보낸다() {
        when(memberRepository.findByProviderAndProviderId("guest", "pid"))
                .thenReturn(Optional.of(Member.builder().id(1L).provider("guest").providerId("pid").build()));
        when(surveyProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(Mockito.mock(com.opicnic.opicnic.domain.SurveyProfile.class)));

        String view = controller.mockExam(user("guest"), new ExtendedModelMap());

        assertThat(view).isEqualTo("redirect:/?guestMock=true");
        verify(mockExamService, never()).createMockExam(any());
        verify(attemptService, never()).createAttempt(any(), any(), any(), any(), any());
    }

    @Test
    void 일반_회원은_그대로_모의고사를_만든다() {
        when(memberRepository.findByProviderAndProviderId("kakao", "pid"))
                .thenReturn(Optional.of(Member.builder().id(2L).provider("kakao").providerId("pid").build()));
        when(surveyProfileRepository.findByMemberId(2L)).thenReturn(Optional.of(Mockito.mock(com.opicnic.opicnic.domain.SurveyProfile.class)));
        when(mockExamService.createMockExam(any())).thenReturn(java.util.List.of());
        when(attemptService.createAttempt(any(), any(), any(), any(), any()))
                .thenReturn(new com.opicnic.opicnic.domain.attempt.PracticeAttempt("a", java.util.List.of(), 2L,
                        com.opicnic.opicnic.domain.enums.PracticeMode.MOCK_EXAM, null, null,
                        java.time.Instant.now(), com.opicnic.opicnic.domain.enums.AttemptStatus.IN_PROGRESS));

        assertThat(controller.mockExam(user("kakao"), new ExtendedModelMap())).isEqualTo("practice/question");
    }
}
