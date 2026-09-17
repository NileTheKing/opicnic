package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// S2("외부 API 실패 30% 주입 시 우리 쪽 호출 증폭 ≤ 1.5배") 측정 전, mock 429 주입이
// FeedbackService.getComboFeedbackStreaming()의 재시도 루프가 실제로 감지하는 429 경로
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
        STTService sttService = new STTService(RestClient.builder(), "dummy-key", false, 0L, 1.0, 0.0, new ObjectMapper());

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
        GroqService groqService = new GroqService(mock(ChatModel.class), new ObjectMapper());
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

    // 감지 로직뿐 아니라 실제 재시도 루프(429 -> 긴 백오프 -> 최종 실패)까지 real STTService를 통해
    // 끝까지 태운다. lastWasRateLimited 분기는 일반 실패(짧은 백오프)보다 훨씬 오래 대기하므로
    // (3000ms<<attempt 기준) 경과 시간으로 429 백오프 경로가 실제로 선택됐음을 확인한다.
    @Test
    @DisplayName("STT가 항상 429를 던지면 3회 재시도 후 실패 카드가 되고, rate-limit 백오프(긴 대기)가 적용된다")
    void sttAlwaysRateLimited_retriesThenFailsWithRateLimitBackoff() {
        STTService sttService = new STTService(RestClient.builder(), "dummy-key", false, 0L, 1.0, 0.0, new ObjectMapper());
        GroqService groqService = Mockito.mock(GroqService.class); // STT 단계에서 항상 실패하므로 호출되지 않아야 함
        FeedbackService feedbackService = new FeedbackService(
                Mockito.mock(ComboPracticeService.class), sttService, groqService, new ObjectMapper());

        long start = System.currentTimeMillis();
        List<FeedbackDTO> results = feedbackService.getComboFeedbackStreaming(
                List.of(new byte[]{1, 2, 3}), List.of(QUESTION));
        long elapsedMs = System.currentTimeMillis() - start;

        FeedbackDTO result = results.get(0);
        assertThat(result.isFailed()).isTrue();
        // rate-limit 백오프만으로도 attempt1->2, attempt2->3 사이에 최소 3000ms + 6000ms = 9000ms가 든다.
        // 일반(non-rate-limit) 백오프였다면 최대 1300+2300=3600ms 수준이라 이 임계값으로 분기를 구분할 수 있다.
        assertThat(elapsedMs).isGreaterThan(8000L);
        Mockito.verifyNoInteractions(groqService);
    }
}
