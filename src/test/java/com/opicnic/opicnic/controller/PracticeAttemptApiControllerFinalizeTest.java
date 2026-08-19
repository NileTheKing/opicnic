package com.opicnic.opicnic.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.config.RateLimiterService;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.AttemptStatus;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.attempt.FeedbackPersistenceService;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// DATA-01 회귀 테스트: finalize()는 DB 저장 전에 attemptService.tryConsume()으로 먼저
// "제출 처리 권한"을 원자적으로 확정해야 한다. 이미 제출된 attempt면 저장을 시도하면 안 된다.
// 자기소개 필터링 자체(어떤 문항이 저장되는지)는 FeedbackPersistenceServiceTest가 검증한다 —
// 그 로직이 컨트롤러에서 FeedbackPersistenceService로 이동했기 때문.
class PracticeAttemptApiControllerFinalizeTest {

    private PracticeAttemptService attemptService;
    private FeedbackPersistenceService feedbackPersistenceService;
    private PracticeAttemptApiController controller;
    private final String attemptId = "attempt-1";

    private void setUp() {
        attemptService = Mockito.mock(PracticeAttemptService.class);
        FeedbackService feedbackService = Mockito.mock(FeedbackService.class);
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        feedbackPersistenceService = Mockito.mock(FeedbackPersistenceService.class);

        controller = new PracticeAttemptApiController(
                attemptService, feedbackService, memberRepository,
                feedbackPersistenceService, new ObjectMapper(), new RateLimiterService());
    }

    private PracticeAttempt attemptWithTwoQuestions() {
        // memberId=null -> rejectIfNotOwner()가 소유권 검사를 건너뛰어 인증 없이도 finalize 로직만 테스트 가능
        return new PracticeAttempt(attemptId, java.util.Arrays.asList(null, 10L), null, PracticeMode.MOCK_EXAM,
                null, null, Instant.now().plusSeconds(3600), AttemptStatus.IN_PROGRESS);
    }

    private MockHttpSession sessionWithCompleteResults() {
        QuestionDto selfIntro = new QuestionDto(null, "Please introduce yourself.", "자기소개", null);
        QuestionDto graded = new QuestionDto(10L, "content", "topic", QuestionType.TYPE_1);
        FeedbackDTO selfIntroResult = FeedbackDTO.builder().question(selfIntro).sttText("hi").build();
        FeedbackDTO gradedResult = FeedbackDTO.builder().question(graded).sttText("answer").overallGrade("IM2").build();

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("practiceFeedbackResults:" + attemptId,
                new HashMap<>(Map.of(0, selfIntroResult, 1, gradedResult)));
        return session;
    }

    @Test
    void finalizeConsumesAttemptBeforeDelegatingPersistence() {
        setUp();
        when(attemptService.requireValidAttempt(attemptId)).thenReturn(attemptWithTwoQuestions());
        when(attemptService.tryConsume(attemptId)).thenReturn(true);

        OAuth2User user = Mockito.mock(OAuth2User.class);

        controller.finalize(attemptId, sessionWithCompleteResults(), user);

        verify(attemptService).tryConsume(attemptId);
        verify(feedbackPersistenceService, times(1)).saveFeedbackResults(any(), any(), any());
    }

    @Test
    void finalizeDoesNotPersistWhenTryConsumeFails() {
        setUp();
        when(attemptService.requireValidAttempt(attemptId)).thenReturn(attemptWithTwoQuestions());
        // 동시 요청 등으로 이미 다른 요청이 제출 처리 권한을 가져간 상황을 흉내낸다.
        when(attemptService.tryConsume(attemptId)).thenReturn(false);

        OAuth2User user = Mockito.mock(OAuth2User.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                controller.finalize(attemptId, sessionWithCompleteResults(), user))
                .isInstanceOf(IllegalStateException.class);

        verify(feedbackPersistenceService, never()).saveFeedbackResults(any(), any(), any());
    }
}
