package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.exception.ExternalCallException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// S2("외부 API 실패 30% 주입 시 우리 쪽 호출 증폭 ≤ 1.5배") 측정 전, mock 429 주입이
// ScoringWorker의 재시도 분기가 실제로 감지하는 429 경로
// (경계에서 ExternalCallException RATE_LIMITED로 옮겨짐)를 그대로 타는지 확인하는 테스트.
// STTService/LlmService는 실제 인스턴스를 mock 모드로 만들어 쓴다 — Mockito.mock으로
// 임의 예외를 던지면 주입 로직 자체(STTService/LlmService가 실제와 같은 예외 타입을 던지는지)는
// 검증되지 않는다.
class FeedbackServiceMockFailureInjectionTest {

    private static final QuestionDto QUESTION = new QuestionDto(1L, "content", "topic", QuestionType.TYPE_1);

    private static boolean isRateLimited(Throwable e) {
        return e instanceof ExternalCallException x && x.reason() == ExternalCallException.Reason.RATE_LIMITED;
    }

    @Test
    @DisplayName("STT mock 429 주입 예외가 RATE_LIMITED로 옮겨진다")
    void sttMock429Exception_isDetectedAsRateLimited() {
        STTService sttService = new STTService(RestClient.builder(), "dummy-key", false, 0L, 1.0, 0.0, 0.0, new ObjectMapper(), new SimpleMeterRegistry());

        Throwable thrown = null;
        try {
            sttService.sendStreamToStt(new byte[]{1}, "a.webm");
        } catch (Throwable e) {
            thrown = e;
        }

        assertThat(thrown).isNotNull();
        assertThat(isRateLimited(thrown)).isTrue();
    }

    @Test
    @DisplayName("LLM(채점) mock 429 주입 예외가 RATE_LIMITED로 옮겨진다")
    void llmMock429Exception_isDetectedAsRateLimited() {
        LlmService llmService = new LlmService(mock(ChatModel.class), new ObjectMapper(), new SimpleMeterRegistry());
        ReflectionTestUtils.setField(llmService, "aiEnabled", false);
        ReflectionTestUtils.setField(llmService, "mockDelayMs", 0L);
        ReflectionTestUtils.setField(llmService, "mock429Rate", 1.0);
        ReflectionTestUtils.setField(llmService, "mock5xxRate", 0.0);

        Throwable thrown = null;
        try {
            llmService.getOpicFeedback("speech", QUESTION);
        } catch (Throwable e) {
            thrown = e;
        }

        assertThat(thrown).isNotNull();
        assertThat(isRateLimited(thrown)).isTrue();
    }

    // 감지 로직뿐 아니라, 워커가 실제로 보는 경로(FeedbackService.transcribe)에서 real STTService의 mock 429가
    // 경계(ExternalCallMetrics.record)를 지나 우리 예외(RATE_LIMITED)로 옮겨져 나오는지 확인한다. 재시도·백오프는 ScoringWorker가
    // 소유하므로(ScoringWorkerTest.backoffMatchesSyncPath) 여기서 루프를 태우지 않는다.
    @Test
    @DisplayName("STT가 항상 429를 던지면 transcribe는 RATE_LIMITED로 옮겨진 예외를 던진다")
    void sttAlwaysRateLimited_transcribeThrowsRateLimited() {
        STTService sttService = new STTService(RestClient.builder(), "dummy-key", false, 0L, 1.0, 0.0, 0.0, new ObjectMapper(), new SimpleMeterRegistry());
        LlmService llmService = Mockito.mock(LlmService.class); // STT 단계에서 실패하므로 호출되지 않아야 함
        FeedbackService feedbackService = new FeedbackService(Mockito.mock(ComboPracticeService.class), sttService, llmService, new ObjectMapper());

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> feedbackService.transcribe(new byte[]{1, 2, 3}, "a.webm"));

        assertThat(thrown).isInstanceOfSatisfying(ExternalCallException.class,
                e -> assertThat(e.reason()).isEqualTo(ExternalCallException.Reason.RATE_LIMITED));
        Mockito.verifyNoInteractions(llmService);
    }
}
