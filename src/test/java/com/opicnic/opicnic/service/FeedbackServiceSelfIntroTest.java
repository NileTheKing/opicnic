package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// PC-01 회귀 테스트: 모의고사 자기소개(questionType=null) 문항이 정상 답변일 때
// NPE 없이 완료되고, 채점/태깅 LLM은 호출되지 않아야 한다.
class FeedbackServiceSelfIntroTest {

    @Test
    void selfIntroductionWithValidAnswerCompletesWithoutGradingCall() {
        ComboPracticeService comboPracticeService = Mockito.mock(ComboPracticeService.class);
        STTService sttService = Mockito.mock(STTService.class);
        GroqService groqService = Mockito.mock(GroqService.class);
        FeedbackService feedbackService = new FeedbackService(comboPracticeService, sttService, groqService, new ObjectMapper());

        when(sttService.sendStreamToStt(any(), any()))
                .thenReturn("My name is Yang and I am a software engineer who enjoys jogging on weekends.");

        QuestionDto selfIntro = new QuestionDto(null,
                "Please introduce yourself.", "자기소개", null);

        List<InputStream> streams = List.of(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        List<FeedbackDTO> results = feedbackService.getComboFeedbackStreaming(streams, List.of(selfIntro));

        assertThat(results).hasSize(1);
        FeedbackDTO result = results.get(0);
        assertThat(result.isFailed()).isFalse();
        assertThat(result.getOverallGrade()).isNull();
        assertThat(result.getOverall()).isNotBlank();

        verifyNoInteractions(groqService);
    }
}
