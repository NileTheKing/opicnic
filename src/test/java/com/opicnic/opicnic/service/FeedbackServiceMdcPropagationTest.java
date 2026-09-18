package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.dto.QuestionDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// MDC는 스레드 로컬이라 StructuredTaskScope.fork()로 만든 가상 스레드엔 자동으로 안 넘어간다.
// AttemptIdMdcFilter가 요청 스레드에 넣은 attemptId가 subtask(STT 호출 시점)에서도 보이는지,
// 그 호출이 실제로 다른 스레드에서 실행됐는지를 같이 확인한다.
class FeedbackServiceMdcPropagationTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("요청 스레드의 attemptId MDC가 fork된 subtask 스레드에서도 보인다")
    void attemptIdMdc_isVisibleInForkedSubtask() {
        AtomicReference<String> mdcInSubtask = new AtomicReference<>();
        AtomicReference<Thread> subtaskThread = new AtomicReference<>();

        STTService sttService = Mockito.mock(STTService.class);
        when(sttService.sendStreamToStt(any(), anyString())).thenAnswer(inv -> {
            mdcInSubtask.set(MDC.get("attemptId"));
            subtaskThread.set(Thread.currentThread());
            return "short"; // 5단어 미만 → 무응답 조기 반환, LLM 호출 없음
        });
        FeedbackService feedbackService = new FeedbackService(
                Mockito.mock(ComboPracticeService.class), sttService, Mockito.mock(GroqService.class),
                new ObjectMapper(), new SimpleMeterRegistry());

        MDC.put("attemptId", "t1");
        feedbackService.getComboFeedbackStreaming(
                List.of(new byte[]{1}), List.of(new QuestionDto(1L, "content", "topic", null)));

        assertThat(subtaskThread.get()).isNotNull().isNotSameAs(Thread.currentThread());
        assertThat(mdcInSubtask.get()).isEqualTo("t1");
        assertThat(MDC.get("attemptId")).isEqualTo("t1"); // 요청 스레드 쪽은 subtask의 clear에 영향받지 않는다
    }
}
