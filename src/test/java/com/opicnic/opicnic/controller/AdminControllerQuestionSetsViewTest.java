package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.config.RateLimiterService;
import com.opicnic.opicnic.domain.QuestionSet;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ADMIN-01 회귀 테스트: /admin/question-sets는 QuestionSet에 없는 difficulty 필드를
// 템플릿에서 참조해 500이 나던 화면이다. 세트가 1개 이상 있어도 정상 렌더링돼야 한다.
@WebMvcTest(controllers = AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerQuestionSetsViewTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QuestionSetRepository questionSetRepository;
    // WebConfig(WebMvcConfigurer)가 @WebMvcTest에 자동 포함되면서 RateLimitInterceptor -> PracticeAttemptService
    // 의존성 체인이 함께 로드되므로 목으로 채워줘야 한다.
    @MockBean
    private PracticeAttemptService practiceAttemptService;
    @MockBean
    private RateLimiterService rateLimiterService;

    @Test
    void listQuestionSetsRendersWithoutErrorWhenSetsExist() throws Exception {
        QuestionSet set = new QuestionSet("테스트 세트", SurveyTopic.JOGGING);
        when(questionSetRepository.findAll()).thenReturn(List.of(set));

        mockMvc.perform(get("/admin/question-sets"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("난이도"))));
    }
}
