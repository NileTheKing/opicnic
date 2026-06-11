package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GeminiServiceTest {

    private GeminiService geminiService;

    @BeforeEach
    void setUp() {
        geminiService = new GeminiService(mock(ChatModel.class), new ObjectMapper());
        ReflectionTestUtils.setField(geminiService, "aiEnabled", false);
        ReflectionTestUtils.setField(geminiService, "mockDelayMs", 0L);
    }

    @Test
    @DisplayName("mock 모드에서 score 필드가 Integer로 반환되어야 한다")
    void mockMode_returnsScoreFieldsAsInteger() {
        QuestionDto question = new QuestionDto(1L, "Tell me about your hobby.", "취미", QuestionType.TYPE_1);

        Map<String, Object> result = geminiService.getOpicFeedback("I like hiking.", question);

        assertThat(result.get("vocabularyScore")).isInstanceOf(Integer.class);
        assertThat(result.get("grammarScore")).isInstanceOf(Integer.class);
        assertThat(result.get("mainPointScore")).isInstanceOf(Integer.class);
        assertThat(result.get("fluencyScore")).isInstanceOf(Integer.class);
        assertThat(result.get("contentScore")).isInstanceOf(Integer.class);
    }

    @Test
    @DisplayName("mock 모드에서 score 값이 1~5 범위여야 한다")
    void mockMode_scoresAreInRange() {
        QuestionDto question = new QuestionDto(1L, "Tell me about your hobby.", "취미", QuestionType.TYPE_1);

        Map<String, Object> result = geminiService.getOpicFeedback("I like hiking.", question);

        for (String key : new String[]{"vocabularyScore", "grammarScore", "mainPointScore", "fluencyScore", "contentScore"}) {
            int score = (Integer) result.get(key);
            assertThat(score).isBetween(1, 5);
        }
    }

    @Test
    @DisplayName("mock 모드에서 overallGrade가 반환되어야 한다")
    void mockMode_returnsOverallGrade() {
        QuestionDto question = new QuestionDto(1L, "Tell me about your hobby.", "취미", QuestionType.TYPE_1);

        Map<String, Object> result = geminiService.getOpicFeedback("I like hiking.", question);

        assertThat(result.get("overallGrade")).isNotNull();
        assertThat(result.get("overallGrade").toString()).isNotBlank();
    }

    @Test
    @DisplayName("mock 모드에서 텍스트 피드백 필드가 모두 반환되어야 한다")
    void mockMode_returnsAllTextFields() {
        QuestionDto question = new QuestionDto(1L, "Tell me about your hobby.", "취미", QuestionType.TYPE_1);

        Map<String, Object> result = geminiService.getOpicFeedback("I like hiking.", question);

        for (String key : new String[]{"vocabulary", "grammar", "mainPoint", "fluency", "content", "overall", "improvements"}) {
            assertThat(result.get(key)).as("필드 %s 누락", key).isNotNull();
        }
    }
}
