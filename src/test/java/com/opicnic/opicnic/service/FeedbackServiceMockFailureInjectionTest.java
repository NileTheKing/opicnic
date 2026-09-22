package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.dto.QuestionDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// S2("외부 API 실패 30% 주입 시 우리 쪽 호출 증폭 ≤ 1.5배") 측정 전, mock 429 주입이
// ScoringWorker의 재시도 분기가 실제로 감지하는 429 경로
// (isRateLimited() -> HttpClientErrorException 429)를 그대로 타는지 확인하는 테스트.
// STTService/GroqService는 실제 인스턴스를 mock 모드로 만들어 쓴다 — Mockito.mock으로
// 임의 예외를 던지면 주입 로직 자체(STTService/GroqService가 실제와 같은 예외 타입을 던지는지)는
// 검증되지 않는다.
class FeedbackServiceMockFailureInjectionTest {

    private static final QuestionDto QUESTION = new QuestionDto(1L, "content", "topic", QuestionType.TYPE_1);

    private static boolean invokeIsRateLimited(Throwable e) throws Exception {
        Method m = FeedbackService.class.getDeclaredMethod("isRateLimited", Throwable.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, e);
    }

    @Test
    @DisplayName("STT mock 429 주입 예외가 FeedbackService.isRateLimited()를 true로 만든다")
    void sttMock429Exception_isDetectedAsRateLimited() throws Exception {
        STTService sttService = new STTService(RestClient.builder(), "dummy-key", false, 0L, 1.0, 0.0, 0.0, new ObjectMapper(), new SimpleMeterRegistry());

        Throwable thrown = null;
        try {
            sttService.sendStreamToStt(new byte[]{1}, "a.webm");
        } catch (Throwable e) {
            thrown = e;
        }

        assertThat(thrown).isNotNull();
        assertThat(invokeIsRateLimited(thrown)).isTrue();
    }

    @Test
    @DisplayName("LLM(채점) mock 429 주입 예외가 FeedbackService.isRateLimited()를 true로 만든다")
    void llmMock429Exception_isDetectedAsRateLimited() throws Exception {
        GroqService groqService = new GroqService(mock(ChatModel.class), new ObjectMapper(), new SimpleMeterRegistry());
        ReflectionTestUtils.setField(groqService, "aiEnabled", false);
        ReflectionTestUtils.setField(groqService, "mockDelayMs", 0L);
        ReflectionTestUtils.setField(groqService, "mock429Rate", 1.0);
        ReflectionTestUtils.setField(groqService, "mock5xxRate", 0.0);

        Throwable thrown = null;
        try {
            groqService.getOpicFeedback("speech", QUESTION);
        } catch (Throwable e) {
            thrown = e;
        }

        assertThat(thrown).isNotNull();
        assertThat(invokeIsRateLimited(thrown)).isTrue();
    }

    // 감지 로직뿐 아니라, 워커가 실제로 보는 경로(FeedbackService.transcribe)에서 real STTService의 mock 429가
    // 그대로 튀어나오고 isRateLimited가 이를 429로 판정하는지 확인한다. 재시도·백오프는 ScoringWorker가
    // 소유하므로(ScoringWorkerTest.backoffMatchesSyncPath) 여기서 루프를 태우지 않는다.
    @Test
    @DisplayName("STT가 항상 429를 던지면 transcribe는 예외를 그대로 던지고, 워커의 429 판정이 true다")
    void sttAlwaysRateLimited_transcribeThrowsRateLimited() {
        STTService sttService = new STTService(RestClient.builder(), "dummy-key", false, 0L, 1.0, 0.0, 0.0, new ObjectMapper(), new SimpleMeterRegistry());
        GroqService groqService = Mockito.mock(GroqService.class); // STT 단계에서 실패하므로 호출되지 않아야 함
        FeedbackService feedbackService = new FeedbackService(Mockito.mock(ComboPracticeService.class), sttService, groqService, new ObjectMapper());

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> feedbackService.transcribe(new byte[]{1, 2, 3}, "a.webm"));

        assertThat(thrown).isNotNull();
        assertThat(FeedbackService.isRateLimited(thrown)).isTrue();
        Mockito.verifyNoInteractions(groqService);
    }
}
