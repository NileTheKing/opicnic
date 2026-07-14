package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.domain.enums.QuestionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GroqServiceTest {

    private GroqService groqService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        groqService = new GroqService(mock(ChatModel.class), objectMapper);
        ReflectionTestUtils.setField(groqService, "aiEnabled", false);
        ReflectionTestUtils.setField(groqService, "mockDelayMs", 0L);
    }

    @Test
    @DisplayName("mock 모드에서 score 필드가 Integer로 반환되어야 한다")
    void mockMode_returnsScoreFieldsAsInteger() {
        QuestionDto question = new QuestionDto(1L, "Tell me about your hobby.", "취미", QuestionType.TYPE_1);

        Map<String, Object> result = groqService.getOpicFeedback("I like hiking.", question);

        assertThat(result.get("expressionScore")).isInstanceOf(Integer.class);
        assertThat(result.get("accuracyScore")).isInstanceOf(Integer.class);
        assertThat(result.get("mainPointScore")).isInstanceOf(Integer.class);
        assertThat(result.get("fluencyScore")).isInstanceOf(Integer.class);
        assertThat(result.get("contentScore")).isInstanceOf(Integer.class);
    }

    @Test
    @DisplayName("mock 모드에서 score 값이 1~5 범위여야 한다 (fluencyScore는 항상 0 - 코드가 별도 계산)")
    void mockMode_scoresAreInRange() {
        QuestionDto question = new QuestionDto(1L, "Tell me about your hobby.", "취미", QuestionType.TYPE_1);

        Map<String, Object> result = groqService.getOpicFeedback("I like hiking.", question);

        for (String key : new String[]{"expressionScore", "accuracyScore", "mainPointScore", "contentScore"}) {
            int score = (Integer) result.get(key);
            assertThat(score).isBetween(1, 5);
        }
        assertThat((Integer) result.get("fluencyScore")).isZero();
    }

    @Test
    @DisplayName("mock 모드에서 진단 텍스트 + quote/fix 필드가 모두 반환되어야 한다")
    void mockMode_returnsAllTextAndExampleFields() {
        QuestionDto question = new QuestionDto(1L, "Tell me about your hobby.", "취미", QuestionType.TYPE_1);

        Map<String, Object> result = groqService.getOpicFeedback("I like hiking.", question);

        for (String key : new String[]{
                "mainPoint", "expression", "accuracy", "content", "improvements",
                "modelAnswer", "modelAnswerComment",
                "mainPointQuote", "mainPointFix", "expressionQuote", "expressionFix",
                "accuracyQuote", "accuracyFix", "contentQuote", "contentFix"}) {
            assertThat(result).as("필드 %s 누락", key).containsKey(key);
        }
    }

    @Test
    @DisplayName("mock 모드에서 태그 추출은 중첩 스키마(expression.vocab/sentence/imagery)를 반환해야 한다")
    void mockMode_extractFeedbackTags_returnsNestedSchema() throws Exception {
        String json = groqService.extractFeedbackTags("TYPE_1", "진단", "진단", "진단", "진단");
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("mainPoint")).isTrue();
        assertThat(node.has("expression")).isTrue();
        assertThat(node.get("expression").has("vocab")).isTrue();
        assertThat(node.get("expression").has("sentence")).isTrue();
        assertThat(node.get("expression").has("imagery")).isTrue();
        assertThat(node.has("accuracy")).isTrue();
        assertThat(node.has("content")).isTrue();
    }

    @Test
    @DisplayName("mock 모드에서 코칭 리포트는 summary/strength/criteria/types를 반환해야 한다")
    void mockMode_getCoachingReport_returnsExpectedSchema() throws Exception {
        String json = groqService.getCoachingReport("【메인포인트】\n- WHY_MISSING: 3/10건", "IH");
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("summary")).isTrue();
        assertThat(node.has("strength")).isTrue();
        assertThat(node.get("criteria").isArray()).isTrue();
        assertThat(node.get("types").isArray()).isTrue();
    }
}
