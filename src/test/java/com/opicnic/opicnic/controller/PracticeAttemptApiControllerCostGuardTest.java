package com.opicnic.opicnic.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.config.RateLimiterService;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.AttemptStatus;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.exception.ApiExceptionHandler;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.FeedbackTagRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// COST-01 회귀 테스트: 답변 제출 1건마다 STT+채점+태깅 LLM 호출이 나가므로, 외부 호출(feedbackService) 전에
// 중복/null index, 문항 수 초과, 이미 성공한 문항 재제출, 과도한 파일 크기/잘못된 MIME을 걸러내야 한다.
@WebMvcTest(controllers = PracticeAttemptApiController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class PracticeAttemptApiControllerCostGuardTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PracticeAttemptService attemptService;
    @MockBean
    private FeedbackService feedbackService;
    @MockBean
    private MemberRepository memberRepository;
    @MockBean
    private FeedbackResultRepository feedbackResultRepository;
    @MockBean
    private FeedbackTagRepository feedbackTagRepository;
    @MockBean
    private RateLimiterService rateLimiterService;

    private static final String ATTEMPT_ID = "attempt-1";

    private PracticeAttempt threeQuestionAttempt() {
        // memberId=null -> rejectIfNotOwner()가 소유권 검사를 건너뛰어 인증 없이도 검증 로직만 테스트 가능
        return new PracticeAttempt(ATTEMPT_ID, List.of(1L, 2L, 3L), null, PracticeMode.COMBO,
                null, null, Instant.now().plusSeconds(3600), AttemptStatus.IN_PROGRESS);
    }

    // 컨트롤러가 request.getParts()(서블릿 Part API)를 쓰므로, MultipartFile 추상화가 아니라
    // MockPart로 실제 getParts()가 채워지도록 구성해야 한다.
    private MockPart audioFile(String name, int sizeBytes, String contentType) {
        return new MockPart("files", name, new byte[sizeBytes], MediaType.parseMediaType(contentType));
    }

    @Test
    void duplicateIndexesAreRejectedBeforeCallingFeedbackService() throws Exception {
        when(attemptService.requireValidAttempt(ATTEMPT_ID)).thenReturn(threeQuestionAttempt());

        mockMvc.perform(multipart("/api/practice-attempts/{id}/answers", ATTEMPT_ID)
                        .part(audioFile("a.webm", 100, "audio/webm"))
                        .part(audioFile("b.webm", 100, "audio/webm"))
                        .param("questionIndexes", "[0,0]"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("중복")));

        verify(feedbackService, never()).getComboFeedbackStreaming(any(), any());
        verify(rateLimiterService, never()).tryConsume(anyInt());
    }

    @Test
    void nullIndexIsRejected() throws Exception {
        when(attemptService.requireValidAttempt(ATTEMPT_ID)).thenReturn(threeQuestionAttempt());

        mockMvc.perform(multipart("/api/practice-attempts/{id}/answers", ATTEMPT_ID)
                        .part(audioFile("a.webm", 100, "audio/webm"))
                        .param("questionIndexes", "[0,null]"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("null")));

        verify(feedbackService, never()).getComboFeedbackStreaming(any(), any());
        verify(rateLimiterService, never()).tryConsume(anyInt());
    }

    @Test
    void tooManyIndexesExceedingAttemptSizeAreRejected() throws Exception {
        when(attemptService.requireValidAttempt(ATTEMPT_ID)).thenReturn(threeQuestionAttempt());

        mockMvc.perform(multipart("/api/practice-attempts/{id}/answers", ATTEMPT_ID)
                        .part(audioFile("a.webm", 100, "audio/webm"))
                        .part(audioFile("b.webm", 100, "audio/webm"))
                        .part(audioFile("c.webm", 100, "audio/webm"))
                        .part(audioFile("d.webm", 100, "audio/webm"))
                        .param("questionIndexes", "[0,1,2,3]"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("초과")));

        verify(feedbackService, never()).getComboFeedbackStreaming(any(), any());
        verify(rateLimiterService, never()).tryConsume(anyInt());
    }

    @Test
    void resubmittingAlreadySuccessfulIndexIsRejected() throws Exception {
        when(attemptService.requireValidAttempt(ATTEMPT_ID)).thenReturn(threeQuestionAttempt());

        MockHttpSession session = new MockHttpSession();
        Map<Integer, FeedbackDTO> existing = new HashMap<>();
        existing.put(0, FeedbackDTO.builder().build());
        session.setAttribute("practiceFeedbackResults:" + ATTEMPT_ID, existing);

        mockMvc.perform(multipart("/api/practice-attempts/{id}/answers", ATTEMPT_ID)
                        .part(audioFile("a.webm", 100, "audio/webm"))
                        .param("questionIndexes", "[0]")
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("이미 채점이 완료된")));

        verify(feedbackService, never()).getComboFeedbackStreaming(any(), any());
        verify(rateLimiterService, never()).tryConsume(anyInt());
    }

    @Test
    void oversizedFileIsRejected() throws Exception {
        when(attemptService.requireValidAttempt(ATTEMPT_ID)).thenReturn(threeQuestionAttempt());
        when(attemptService.restoreQuestionsForIndexes(any(), anyList()))
                .thenReturn(List.of(new QuestionDto(1L, "q", "topic", QuestionType.TYPE_1)));

        mockMvc.perform(multipart("/api/practice-attempts/{id}/answers", ATTEMPT_ID)
                        .part(audioFile("a.webm", 16 * 1024 * 1024, "audio/webm")) // 16MB > 15MB 한도
                        .param("questionIndexes", "[0]"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("너무 큽니다")));

        verify(feedbackService, never()).getComboFeedbackStreaming(any(), any());
        verify(rateLimiterService, never()).tryConsume(anyInt());
    }

    @Test
    void disallowedContentTypeIsRejected() throws Exception {
        when(attemptService.requireValidAttempt(ATTEMPT_ID)).thenReturn(threeQuestionAttempt());
        when(attemptService.restoreQuestionsForIndexes(any(), anyList()))
                .thenReturn(List.of(new QuestionDto(1L, "q", "topic", QuestionType.TYPE_1)));

        mockMvc.perform(multipart("/api/practice-attempts/{id}/answers", ATTEMPT_ID)
                        .part(audioFile("a.exe", 100, "application/octet-stream"))
                        .param("questionIndexes", "[0]"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("지원하지 않는 파일 형식")));

        verify(feedbackService, never()).getComboFeedbackStreaming(any(), any());
        verify(rateLimiterService, never()).tryConsume(anyInt());
    }

    @Test
    void validSubmissionConsumesRateLimitAfterGuardsThenCallsFeedbackService() throws Exception {
        when(attemptService.requireValidAttempt(ATTEMPT_ID)).thenReturn(threeQuestionAttempt());
        QuestionDto question = new QuestionDto(1L, "q", "topic", QuestionType.TYPE_1);
        when(attemptService.restoreQuestionsForIndexes(any(), anyList())).thenReturn(List.of(question));
        when(rateLimiterService.tryConsume(anyInt())).thenReturn(true);
        when(feedbackService.getComboFeedbackStreaming(any(), any()))
                .thenReturn(List.of(FeedbackDTO.builder().question(question).build()));

        mockMvc.perform(multipart("/api/practice-attempts/{id}/answers", ATTEMPT_ID)
                        .part(audioFile("a.webm", 1024, "audio/webm"))
                        .param("questionIndexes", "[0]"))
                .andExpect(status().isOk());

        // 1문항 제출 -> 1 소비. rateLimiterService는 검증(위 6개 테스트)을 통과한 뒤에만 호출돼야 한다.
        verify(rateLimiterService).tryConsume(1);
        verify(feedbackService).getComboFeedbackStreaming(any(), any());
    }

    @Test
    void rateLimitExceededReturns429AndSkipsFeedbackService() throws Exception {
        when(attemptService.requireValidAttempt(ATTEMPT_ID)).thenReturn(threeQuestionAttempt());
        QuestionDto question = new QuestionDto(1L, "q", "topic", QuestionType.TYPE_1);
        when(attemptService.restoreQuestionsForIndexes(any(), anyList())).thenReturn(List.of(question));
        when(rateLimiterService.tryConsume(anyInt())).thenReturn(false);

        mockMvc.perform(multipart("/api/practice-attempts/{id}/answers", ATTEMPT_ID)
                        .part(audioFile("a.webm", 1024, "audio/webm"))
                        .param("questionIndexes", "[0]"))
                .andExpect(status().isTooManyRequests());

        verify(feedbackService, never()).getComboFeedbackStreaming(any(), any());
    }

    @Test
    void selfIntroductionQuestionIsExcludedFromRateLimitCost() throws Exception {
        // attempt: index 0 = 자기소개(id=null), index 1 = 실제 채점 문항
        PracticeAttempt attempt = new PracticeAttempt(ATTEMPT_ID, java.util.Arrays.asList(null, 10L), null,
                PracticeMode.MOCK_EXAM, null, null, Instant.now().plusSeconds(3600), AttemptStatus.IN_PROGRESS);
        when(attemptService.requireValidAttempt(ATTEMPT_ID)).thenReturn(attempt);

        QuestionDto selfIntro = new QuestionDto(null, "자기소개", "자기소개", null);
        QuestionDto graded = new QuestionDto(10L, "q", "topic", QuestionType.TYPE_1);
        when(attemptService.restoreQuestionsForIndexes(any(), anyList())).thenReturn(List.of(selfIntro, graded));
        when(rateLimiterService.tryConsume(anyInt())).thenReturn(true);
        when(feedbackService.getComboFeedbackStreaming(any(), any())).thenReturn(List.of(
                FeedbackDTO.builder().question(selfIntro).build(),
                FeedbackDTO.builder().question(graded).build()));

        mockMvc.perform(multipart("/api/practice-attempts/{id}/answers", ATTEMPT_ID)
                        .part(audioFile("a.webm", 100, "audio/webm"))
                        .part(audioFile("b.webm", 100, "audio/webm"))
                        .param("questionIndexes", "[0,1]"))
                .andExpect(status().isOk());

        // 2문항 제출했지만 자기소개(index 0) 제외하고 1만 소비돼야 한다.
        verify(rateLimiterService).tryConsume(1);
    }
}
