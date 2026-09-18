package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.dto.QuestionDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

// 외부 호출 RED 계측 — mock 경로도 실제 경로와 같은 Timer(opicnic.external.call)에 outcome 태그로 기록되는지.
// 2026-08-31 모델 소멸 장애가 기존 지표에 안 잡힌 이유가 이 지표의 부재였다.
class ExternalCallMetricsTest {

    private static final QuestionDto QUESTION = new QuestionDto(1L, "content", "topic", QuestionType.TYPE_1);

    private static double count(SimpleMeterRegistry registry, String kind, String outcome) {
        var timer = registry.find(ExternalCallMetrics.TIMER_NAME)
                .tags("provider", "groq", "kind", kind, "outcome", outcome).timer();
        return timer == null ? 0 : timer.count();
    }

    @Test
    @DisplayName("STT mock 429 주입이면 kind=stt outcome=429 타이머 count가 1 증가한다")
    void sttMock429_recordsOutcome429() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        STTService sttService = new STTService(RestClient.builder(), "dummy-key", false, 0L, 1.0, 0.0, 0.0,
                new ObjectMapper(), registry);

        assertThatThrownBy(() -> sttService.sendStreamToStt(new byte[]{1}, "a.webm")).isNotNull();

        assertThat(count(registry, "stt", "429")).isEqualTo(1);
        assertThat(count(registry, "stt", "ok")).isEqualTo(0);
    }

    @Test
    @DisplayName("STT mock 정상 반환이면 kind=stt outcome=ok 타이머 count가 1 증가한다")
    void sttMockOk_recordsOutcomeOk() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        STTService sttService = new STTService(RestClient.builder(), "dummy-key", false, 0L, 0.0, 0.0, 0.0,
                new ObjectMapper(), registry);

        sttService.sendStreamToStt(new byte[]{1}, "a.webm");

        assertThat(count(registry, "stt", "ok")).isEqualTo(1);
    }

    @Test
    @DisplayName("채점 mock 503 주입이면 kind=score outcome=5xx, 정상이면 outcome=ok로 기록된다")
    void scoreMock_recordsOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GroqService groqService = new GroqService(mock(ChatModel.class), new ObjectMapper(), registry);
        ReflectionTestUtils.setField(groqService, "aiEnabled", false);
        ReflectionTestUtils.setField(groqService, "mockDelayMs", 0L);
        ReflectionTestUtils.setField(groqService, "mock429Rate", 0.0);
        ReflectionTestUtils.setField(groqService, "mock5xxRate", 1.0);

        assertThatThrownBy(() -> groqService.getOpicFeedback("speech", QUESTION)).isNotNull();
        assertThat(count(registry, "score", "5xx")).isEqualTo(1);

        ReflectionTestUtils.setField(groqService, "mock5xxRate", 0.0);
        groqService.getOpicFeedback("speech", QUESTION);
        assertThat(count(registry, "score", "ok")).isEqualTo(1);
    }

    @Test
    @DisplayName("outcome 판정: 타임아웃 계열은 timeout, Spring AI 래핑 예외는 메시지의 상태 코드로, 나머지는 error")
    void outcomeOf_classifiesExceptions() {
        assertThat(ExternalCallMetrics.outcomeOf(new ResourceAccessException("I/O", new SocketTimeoutException()))).isEqualTo("timeout");
        assertThat(ExternalCallMetrics.outcomeOf(new RuntimeException("wrap", new SocketTimeoutException()))).isEqualTo("timeout");
        assertThat(ExternalCallMetrics.outcomeOf(new NonTransientAiException("429 - Rate limit reached"))).isEqualTo("429");
        assertThat(ExternalCallMetrics.outcomeOf(new NonTransientAiException("404 - model not found"))).isEqualTo("error");
        assertThat(ExternalCallMetrics.outcomeOf(new TransientAiException("503 - Service Unavailable"))).isEqualTo("5xx");
        assertThat(ExternalCallMetrics.outcomeOf(new IllegalStateException("parse"))).isEqualTo("error");
    }
}
