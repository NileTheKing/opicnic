package com.opicnic.opicnic.service;

import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.domain.enums.QuestionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private ComboPracticeService comboPracticeService;
    @Mock
    private STTService sttService;
    @Mock
    private GeminiService geminiService;

    private FeedbackService feedbackService;

    private static final QuestionDto QUESTION =
            new QuestionDto(1L, "Tell me about your hobby.", "취미", QuestionType.TYPE_1);

    @BeforeEach
    void setUp() {
        feedbackService = new FeedbackService(comboPracticeService, sttService, geminiService);
    }

    @Test
    @DisplayName("LLM 응답의 score 필드가 FeedbackDTO에 올바르게 매핑되어야 한다")
    void scoreFieldsMappedToDto() throws Exception {
        when(sttService.sendStreamToStt(any(InputStream.class), any())).thenReturn("I like hiking.");
        when(geminiService.getOpicFeedback(any(), any())).thenReturn(Map.ofEntries(
                Map.entry("vocabulary", "어휘가 적절합니다."),
                Map.entry("vocabularyScore", 4),
                Map.entry("grammar", "문법이 정확합니다."),
                Map.entry("grammarScore", 3),
                Map.entry("mainPoint", "메인포인트가 명확합니다."),
                Map.entry("mainPointScore", 5),
                Map.entry("fluency", "발화가 자연스럽습니다."),
                Map.entry("fluencyScore", 3),
                Map.entry("content", "내용이 충실합니다."),
                Map.entry("contentScore", 4),
                Map.entry("overall", "전반적으로 IM 수준입니다."),
                Map.entry("overallGrade", "IM2"),
                Map.entry("improvements", "더 다양한 표현을 사용하세요.")
        ));

        List<InputStream> streams = List.of(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        List<FeedbackDTO> results = feedbackService.getComboFeedbackSequential(streams, List.of(QUESTION));

        assertThat(results).hasSize(1);
        FeedbackDTO dto = results.get(0);

        assertThat(dto.isFailed()).isFalse();
        assertThat(dto.getVocabularyScore()).isEqualTo(4);
        assertThat(dto.getGrammarScore()).isEqualTo(3);
        assertThat(dto.getMainPointScore()).isEqualTo(5);
        assertThat(dto.getFluencyScore()).isEqualTo(3);
        assertThat(dto.getContentScore()).isEqualTo(4);
        assertThat(dto.getOverallGrade()).isEqualTo("IM2");
    }

    @Test
    @DisplayName("LLM 응답에 score 필드가 없으면 null로 처리해야 한다")
    void missingScoreFieldsResultInNull() throws Exception {
        when(sttService.sendStreamToStt(any(InputStream.class), any())).thenReturn("Some text.");
        when(geminiService.getOpicFeedback(any(), any())).thenReturn(Map.of(
                "vocabulary", "OK",
                "grammar", "OK",
                "mainPoint", "OK",
                "fluency", "OK",
                "content", "OK",
                "overall", "IM1",
                "improvements", "Keep going."
        ));

        List<InputStream> streams = List.of(new ByteArrayInputStream(new byte[]{1}));
        List<FeedbackDTO> results = feedbackService.getComboFeedbackSequential(streams, List.of(QUESTION));

        FeedbackDTO dto = results.get(0);
        assertThat(dto.getVocabularyScore()).isNull();
        assertThat(dto.getOverallGrade()).isNull();
    }

    @Test
    @DisplayName("STT/LLM 실패 시 failed=true이고 score는 null이어야 한다")
    void onFailure_dtoIsMarkedFailed() throws Exception {
        when(sttService.sendStreamToStt(any(InputStream.class), any()))
                .thenThrow(new RuntimeException("STT 서버 오류"));

        List<InputStream> streams = List.of(new ByteArrayInputStream(new byte[]{1}));
        List<FeedbackDTO> results = feedbackService.getComboFeedbackSequential(streams, List.of(QUESTION));

        FeedbackDTO dto = results.get(0);
        assertThat(dto.isFailed()).isTrue();
        assertThat(dto.getVocabularyScore()).isNull();
        assertThat(dto.getOverallGrade()).isNull();
    }
}
