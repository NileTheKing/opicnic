package com.opicnic.opicnic.exception;

import com.opicnic.opicnic.controller.AdminQuestionSetApiController;
import com.opicnic.opicnic.controller.PracticeAttemptApiController;
import com.opicnic.opicnic.config.RateLimiterService;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.QuestionAssemblyService;
import com.opicnic.opicnic.service.attempt.FeedbackPersistenceService;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// API-01 회귀 테스트: 클라이언트 실수(깨진 JSON body, URL 파라미터 타입 불일치)가 서버 장애(500)가
// 아니라 400으로 처리되는지 검증. 이전엔 ApiExceptionHandler의 catch-all(Exception -> 500)로
// 떨어져서 서버 장애 지표와 클라이언트 오류가 섞였다.
@WebMvcTest(controllers = {AdminQuestionSetApiController.class, PracticeAttemptApiController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QuestionSetRepository questionSetRepository;
    @MockBean
    private QuestionAssemblyService questionAssemblyService;
    @MockBean
    private PracticeAttemptService attemptService;
    @MockBean
    private FeedbackService feedbackService;
    @MockBean
    private MemberRepository memberRepository;
    @MockBean
    private FeedbackPersistenceService feedbackPersistenceService;
    @MockBean
    private RateLimiterService rateLimiterService;

    @Test
    void malformedTypeInPathVariableReturns400NotServerError() throws Exception {
        mockMvc.perform(put("/api/admin/question-sets/not-a-long")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"topic\":\"JOGGING\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonBodyReturns400NotServerError() throws Exception {
        mockMvc.perform(post("/api/admin/question-sets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest());
    }

    // REVIEW-05 회귀 테스트: @RequestBody가 JSON을 기대하는 엔드포인트에 다른 Content-Type으로
    // 요청하면 Spring이 HttpMediaTypeNotSupportedException을 던진다. catch-all(500)로 새지 않고
    // 415로 응답해야 한다.
    @Test
    void unsupportedContentTypeReturns415NotServerError() throws Exception {
        mockMvc.perform(post("/api/admin/question-sets")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name=x&topic=JOGGING"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
