package com.opicnic.opicnic.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.config.RateLimiterService;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.AttemptStatus;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.FinalizeResponseDto;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.QuestionRepository;
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.attempt.CaffeinePracticeAttemptStore;
import com.opicnic.opicnic.service.attempt.FeedbackPersistenceService;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// REVIEW-01 회귀 테스트: finalize()가 IN_PROGRESS -> FINALIZING -> SUBMITTED 3단계로 동작해야 하는
// 세 가지 요구사항(실패 복구 / 동시 요청 차단 / 성공 재요청 멱등 응답)을 실제 CaffeinePracticeAttemptStore +
// PracticeAttemptService(목이 아닌 진짜 원자적 전이)로 함께 고정한다. FeedbackPersistenceService만
// mock으로 DB 저장 성공/실패를 제어한다.
class PracticeAttemptApiControllerFinalizeTest {

    private final String attemptId = "attempt-1";

    private PracticeAttemptService attemptService;
    private FeedbackPersistenceService feedbackPersistenceService;
    private PracticeAttemptApiController controller;

    private void setUp() {
        CaffeinePracticeAttemptStore store = new CaffeinePracticeAttemptStore();
        store.save(attemptWithTwoQuestions());
        attemptService = new PracticeAttemptService(store, Mockito.mock(QuestionRepository.class));

        FeedbackService feedbackService = Mockito.mock(FeedbackService.class);
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        feedbackPersistenceService = Mockito.mock(FeedbackPersistenceService.class);

        controller = new PracticeAttemptApiController(
                attemptService, feedbackService, memberRepository,
                feedbackPersistenceService, new ObjectMapper(), new RateLimiterService(new StandardEnvironment()));
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
    void finalizeStartsFinalizingBeforeDelegatingPersistenceThenConfirmsSubmitted() {
        setUp();
        OAuth2User user = Mockito.mock(OAuth2User.class);

        ResponseEntity<?> response = controller.finalize(attemptId, sessionWithCompleteResults(), user);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(feedbackPersistenceService, times(1)).saveFeedbackResults(any(), any(), any());
        assertThat(attemptService.requireAttemptForFinalize(attemptId).status()).isEqualTo(AttemptStatus.SUBMITTED);
    }

    // 동시 요청 차단: 이미 다른 요청이 FINALIZING을 선점한 상태에서 두 번째 요청이 들어오면
    // DB 저장을 시도하면 안 된다.
    @Test
    void finalizeDoesNotPersistWhenAnotherRequestIsAlreadyFinalizing() {
        setUp();
        // 다른 스레드가 이미 FINALIZING으로 전이시킨 상황을 흉내낸다.
        assertThat(attemptService.tryStartFinalizing(attemptId)).isTrue();

        OAuth2User user = Mockito.mock(OAuth2User.class);

        assertThatThrownBy(() -> controller.finalize(attemptId, sessionWithCompleteResults(), user))
                .isInstanceOf(IllegalStateException.class);

        verify(feedbackPersistenceService, never()).saveFeedbackResults(any(), any(), any());
        // 여전히 FINALIZING이어야 한다 -- 두 번째 요청이 함부로 되돌리거나 확정하면 안 된다.
        assertThat(attemptService.requireAttemptForFinalize(attemptId).status()).isEqualTo(AttemptStatus.FINALIZING);
    }

    // 실패 복구: DB 저장이 실패하면 SUBMITTED로 확정되지 않고 IN_PROGRESS로 되돌아가 재시도할 수 있어야 한다.
    @Test
    void dbFailureDuringPersistenceRevertsToInProgressAndAllowsRetry() {
        setUp();
        OAuth2User user = Mockito.mock(OAuth2User.class);
        MockHttpSession session = sessionWithCompleteResults();

        doThrow(new RuntimeException("DB down"))
                .doNothing()
                .when(feedbackPersistenceService).saveFeedbackResults(any(), any(), any());

        // 1차 시도: DB 저장 실패
        assertThatThrownBy(() -> controller.finalize(attemptId, session, user))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB down");

        // 복구됐으므로 IN_PROGRESS로 되돌아가 있어야 한다 -- SUBMITTED로 굳어버리면
        // 이 세션은 영원히 "이미 제출된 세션"으로 막혀 사용자가 다시 제출할 방법이 없어진다.
        assertThat(attemptService.requireAttemptForFinalize(attemptId).status()).isEqualTo(AttemptStatus.IN_PROGRESS);
        verify(feedbackPersistenceService, times(1)).saveFeedbackResults(any(), any(), any());

        // 2차 시도(재시도): 이번엔 DB 저장 성공 -> 실제로 완료돼야 한다.
        // 실패 시 세션 결과를 지우지 않았으므로 재시도에도 그대로 남아있다.
        ResponseEntity<?> retryResponse = controller.finalize(attemptId, session, user);

        assertThat(retryResponse.getStatusCode().is2xxSuccessful()).isTrue();
        verify(feedbackPersistenceService, times(2)).saveFeedbackResults(any(), any(), any());
        assertThat(attemptService.requireAttemptForFinalize(attemptId).status()).isEqualTo(AttemptStatus.SUBMITTED);
    }

    // 성공 재요청 멱등 응답: 이미 SUBMITTED로 끝난 attempt에 다시 finalize가 오면(더블클릭, 응답을
    // 못 받은 클라이언트의 재시도) 410이 아니라 같은 성공 응답을 그대로 돌려주고, DB 저장은
    // 다시 일어나면 안 된다.
    @Test
    void secondFinalizeAfterSuccessReturnsSameResultIdempotentlyWithoutReSaving() {
        setUp();
        OAuth2User user = Mockito.mock(OAuth2User.class);

        ResponseEntity<?> first = controller.finalize(attemptId, sessionWithCompleteResults(), user);
        assertThat(first.getStatusCode().is2xxSuccessful()).isTrue();
        FinalizeResponseDto firstBody = (FinalizeResponseDto) first.getBody();

        // 같은 세션(브라우저)에서 다시 finalize를 호출 -- 응답을 못 받아서 재시도한 상황을 흉내낸다.
        // 이미 practiceFeedbackResults 세션 attribute는 지워졌지만, 이 경로는 SUBMITTED 분기에서
        // 바로 성공 응답을 돌려주므로 세션 결과 map을 다시 읽을 필요가 없다.
        ResponseEntity<?> second = controller.finalize(attemptId, new MockHttpSession(), user);

        assertThat(second.getStatusCode().is2xxSuccessful()).isTrue();
        FinalizeResponseDto secondBody = (FinalizeResponseDto) second.getBody();
        assertThat(secondBody.resultUrl()).isEqualTo(firstBody.resultUrl());
        verify(feedbackPersistenceService, times(1)).saveFeedbackResults(any(), any(), any());
    }
}
