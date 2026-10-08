package com.opicnic.opicnic.service;

import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

// 등급은 LLM level + 단어 수 상한 (docs/performance/2026-10-07-grading-calibration)
class FeedbackServiceLevelGradeTest {

    private static String words(int n) {
        return "word ".repeat(n).trim();
    }

    @Test
    void 충분히_길면_LLM_등급_그대로() {
        assertThat(FeedbackService.capByLength("AL", words(200))).isEqualTo("AL");
        assertThat(FeedbackService.capByLength("im2", words(200))).isEqualTo("IM2");
    }

    @Test
    void 짧은_답은_상한까지만_내린다() {
        assertThat(FeedbackService.capByLength("AL", words(20))).isEqualTo("IM1");
        assertThat(FeedbackService.capByLength("AL", words(50))).isEqualTo("IM2");
        assertThat(FeedbackService.capByLength("AL", words(80))).isEqualTo("IM3");
        assertThat(FeedbackService.capByLength("AL", words(110))).isEqualTo("IH");
    }

    @Test
    void NH도_등급으로_받는다() {
        assertThat(FeedbackService.capByLength("NH", words(20))).isEqualTo("NH");
    }

    @Test
    void 상한은_올려_주지_않는다() {
        assertThat(FeedbackService.capByLength("IL", words(20))).isEqualTo("IL");
        assertThat(FeedbackService.capByLength("IM1", words(300))).isEqualTo("IM1");
    }

    @Test
    void level이_없거나_이상하면_null_예전_계산으로() {
        assertThat(FeedbackService.capByLength(null, words(100))).isNull();
        assertThat(FeedbackService.capByLength("NL", words(100))).isNull();
        assertThat(FeedbackService.capByLength("", words(100))).isNull();
    }

    @Test
    void 프롬프트_출력_양식과_스키마의_키가_같다() throws Exception {
        var schema = new com.fasterxml.jackson.databind.ObjectMapper().readTree(LlmService.SCORING_SCHEMA);
        var required = new java.util.TreeSet<String>();
        schema.get("required").forEach(n -> required.add(n.asText()));
        var inPrompt = new java.util.TreeSet<String>();
        var m = java.util.regex.Pattern.compile("\"(\\w+)\":").matcher(LlmService.SCORING_PROMPT.substring(LlmService.SCORING_PROMPT.indexOf("# 5. 출력")));
        while (m.find()) inPrompt.add(m.group(1));
        inPrompt.removeAll(java.util.Set.of("textType", "timeFrames", "cohesion", "vocabulary", "errors"));
        assertThat(inPrompt).isEqualTo(required);
    }

    @Test
    void 스키마의_등급_목록이_서버_등급과_같다() throws Exception {
        var schema = new com.fasterxml.jackson.databind.ObjectMapper().readTree(LlmService.SCORING_SCHEMA);
        var levels = new java.util.ArrayList<String>();
        schema.get("properties").get("level").get("enum").forEach(n -> levels.add(n.asText()));
        assertThat(levels).isEqualTo(FeedbackService.LEVELS);
    }
}
