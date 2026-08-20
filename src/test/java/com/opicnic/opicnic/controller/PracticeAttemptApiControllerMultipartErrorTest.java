package com.opicnic.opicnic.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.config.RateLimiterService;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.AttemptStatus;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.exception.PayloadTooLargeException;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.attempt.FeedbackPersistenceService;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// REVIEW-05 회귀 테스트: 이 컨트롤러는 Spring MultipartResolver를 거치지 않고 request.getParts()로
// 직접 파싱한다. 컨테이너가 요청 크기 한도 초과나 잘못된 Content-Type으로 IOException/ServletException을
// 던지면, 이전엔 그대로 흘러 ApiExceptionHandler의 catch-all(500)로 떨어졌다. 이제는 크기 초과를
// PayloadTooLargeException(413)으로, 그 외 파싱 실패를 IllegalArgumentException(400)으로 구분해 던져야 한다.
class PracticeAttemptApiControllerMultipartErrorTest {

    private static final String ATTEMPT_ID = "attempt-1";

    private PracticeAttemptApiController newController(PracticeAttemptService attemptService) {
        return new PracticeAttemptApiController(
                attemptService,
                Mockito.mock(FeedbackService.class),
                Mockito.mock(MemberRepository.class),
                Mockito.mock(FeedbackPersistenceService.class),
                new ObjectMapper(),
                new RateLimiterService(new StandardEnvironment()));
    }

    private PracticeAttempt oneQuestionAttempt() {
        return new PracticeAttempt(ATTEMPT_ID, List.of(1L), null, PracticeMode.COMBO,
                null, null, Instant.now().plusSeconds(3600), AttemptStatus.IN_PROGRESS);
    }

    private HttpServletRequest requestThatFailsGetParts(Throwable toThrow) throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpSession session = Mockito.mock(HttpSession.class);
        when(request.getParameter("questionIndexes")).thenReturn("[0]");
        when(request.getSession()).thenReturn(session);
        if (toThrow instanceof IOException ioException) {
            when(request.getParts()).thenThrow(ioException);
        } else {
            when(request.getParts()).thenThrow((ServletException) toThrow);
        }
        return request;
    }

    private static class FakeSizeLimitExceededException extends RuntimeException {
        FakeSizeLimitExceededException(String message) { super(message); }
    }

    @Test
    void sizeRelatedFailureMapsToPayloadTooLarge() throws Exception {
        PracticeAttemptService attemptService = Mockito.mock(PracticeAttemptService.class);
        when(attemptService.requireValidAttempt(ATTEMPT_ID)).thenReturn(oneQuestionAttempt());
        when(attemptService.restoreQuestionsForIndexes(Mockito.eq(ATTEMPT_ID), Mockito.anyList()))
                .thenReturn(List.of(new QuestionDto(1L, "q", "topic", QuestionType.TYPE_1)));

        IOException withSizeCause = new IOException(
                "request too large", new FakeSizeLimitExceededException("part exceeds max size"));
        HttpServletRequest request = requestThatFailsGetParts(withSizeCause);

        PracticeAttemptApiController controller = newController(attemptService);

        assertThatThrownBy(() -> controller.submitAnswers(ATTEMPT_ID, request, null))
                .isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void nonSizeParsingFailureMapsToBadRequest() throws Exception {
        PracticeAttemptService attemptService = Mockito.mock(PracticeAttemptService.class);
        when(attemptService.requireValidAttempt(ATTEMPT_ID)).thenReturn(oneQuestionAttempt());
        when(attemptService.restoreQuestionsForIndexes(Mockito.eq(ATTEMPT_ID), Mockito.anyList()))
                .thenReturn(List.of(new QuestionDto(1L, "q", "topic", QuestionType.TYPE_1)));

        ServletException notMultipart = new ServletException("the request doesn't contain multipart/form-data");
        HttpServletRequest request = requestThatFailsGetParts(notMultipart);

        PracticeAttemptApiController controller = newController(attemptService);

        assertThatThrownBy(() -> controller.submitAnswers(ATTEMPT_ID, request, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
