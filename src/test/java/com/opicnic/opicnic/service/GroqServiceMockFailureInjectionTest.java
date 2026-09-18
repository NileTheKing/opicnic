package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.dto.QuestionDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

// S2 측정용 실패 주입 스위치(LLM_MOCK_429_RATE/LLM_MOCK_5XX_RATE/LLM_MOCK_TIMEOUT_RATE) 회귀 테스트.
// aiEnabled=false(mock) 경로 중 채점(getOpicFeedback)에만 있는 로직이라 실제 Groq 호출 없이 검증한다.
// 태깅(extractFeedbackTags)은 스위치 대상이 아니므로 여기서 건드리지 않는다.
class GroqServiceMockFailureInjectionTest {

    private final QuestionDto question = new QuestionDto(1L, "content", "topic", QuestionType.TYPE_1);

    private GroqService newMockGroqService(double rate429, double rate5xx) {
        return newMockGroqService(rate429, rate5xx, 0.0);
    }

    private GroqService newMockGroqService(double rate429, double rate5xx, double rateTimeout) {
        GroqService groqService = new GroqService(mock(ChatModel.class), new ObjectMapper(), new SimpleMeterRegistry());
        ReflectionTestUtils.setField(groqService, "aiEnabled", false);
        ReflectionTestUtils.setField(groqService, "mockDelayMs", 0L);
        ReflectionTestUtils.setField(groqService, "mock429Rate", rate429);
        ReflectionTestUtils.setField(groqService, "mock5xxRate", rate5xx);
        ReflectionTestUtils.setField(groqService, "mockTimeoutRate", rateTimeout);
        return groqService;
    }

    @Test
    @DisplayName("LLM_MOCK_TIMEOUT_RATE=1.0이면 채점 호출이 항상 read-timeout 형태(ResourceAccessException ← SocketTimeoutException)를 던진다")
    void rateTimeoutOne_alwaysThrowsTimeout() {
        GroqService groqService = newMockGroqService(0.0, 0.0, 1.0);

        assertThatThrownBy(() -> groqService.getOpicFeedback("speech", question))
                .isInstanceOf(ResourceAccessException.class)
                .hasCauseInstanceOf(java.net.SocketTimeoutException.class);
    }

    @Test
    @DisplayName("LLM_MOCK_429_RATE=1.0이면 채점 호출이 항상 429(HttpClientErrorException)를 던진다")
    void rate429One_alwaysThrows429() {
        GroqService groqService = newMockGroqService(1.0, 0.0);

        assertThatThrownBy(() -> groqService.getOpicFeedback("speech", question))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    @DisplayName("LLM_MOCK_5XX_RATE=1.0이면 채점 호출이 항상 503(HttpServerErrorException)을 던진다")
    void rate5xxOne_alwaysThrows5xx() {
        GroqService groqService = newMockGroqService(0.0, 1.0);

        assertThatThrownBy(() -> groqService.getOpicFeedback("speech", question))
                .isInstanceOf(HttpServerErrorException.class)
                .satisfies(e -> assertThat(((HttpServerErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    @DisplayName("두 rate가 0(기본값)이면 기존처럼 항상 성공한다")
    void ratesZero_alwaysSucceeds() {
        GroqService groqService = newMockGroqService(0.0, 0.0);

        var result = groqService.getOpicFeedback("speech", question);

        assertThat(result).containsKey("mainPointScore");
    }
}
