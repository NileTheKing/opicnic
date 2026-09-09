package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 2026-09-09 회귀 테스트: 채점 LLM만 실패해도 재시도 루프가 STT부터 다시 돌던 문제.
// 15문항 x 재시도로 STT 호출이 43건까지 불어나 Groq Whisper RPM 20을 자가 초과했고
// 429가 21번 나서 43건 중 22건만 성공했다(2026-08-31 실측). 채점 LLM이 처음 2번 실패하고
// 3번째에 성공하는 시나리오에서, STT는 정확히 1번만 호출돼야 한다.
class FeedbackServiceSttRetryScopeTest {

    private FeedbackService newService(GroqService groqService, STTService sttService) {
        return new FeedbackService(Mockito.mock(ComboPracticeService.class), sttService, groqService, new ObjectMapper());
    }

    private String emptyTagsJson() {
        return "{\"mainPoint\":[],\"expression\":{\"vocab\":[],\"sentence\":[],\"imagery\":[]},\"accuracy\":[],\"content\":[]}";
    }

    @Test
    void sttIsCalledOnlyOnceWhenScoringLlmFailsThenSucceeds() {
        STTService sttService = Mockito.mock(STTService.class);
        GroqService groqService = Mockito.mock(GroqService.class);
        FeedbackService feedbackService = newService(groqService, sttService);

        when(sttService.sendStreamToStt(any(), any())).thenReturn(
                "This is a sufficiently long answer so that it passes the minimum word count guard for STT text.");

        Map<String, Object> goodFeedback = Map.of(
                "mainPoint", "양호", "mainPointScore", 3,
                "expression", "양호", "expressionScore", 3,
                "accuracy", "양호", "accuracyScore", 3,
                "content", "양호", "contentScore", 3
        );
        // 채점 LLM이 처음 2번은 실패하고 3번째에 성공한다.
        when(groqService.getOpicFeedback(any(), any()))
                .thenThrow(new RuntimeException("groq scoring failed 1"))
                .thenThrow(new RuntimeException("groq scoring failed 2"))
                .thenReturn(goodFeedback);
        when(groqService.extractFeedbackTags(any(), any(), any(), any(), any())).thenReturn(emptyTagsJson());

        QuestionDto question = new QuestionDto(1L, "content", "topic", QuestionType.TYPE_1);
        List<byte[]> streams = List.of(new byte[]{1, 2, 3});
        List<FeedbackDTO> results = feedbackService.getComboFeedbackStreaming(streams, List.of(question));

        FeedbackDTO result = results.get(0);
        assertThat(result.isFailed()).isFalse();
        verify(sttService, times(1)).sendStreamToStt(any(), any());
    }
}
