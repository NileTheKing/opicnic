package com.opicnic.opicnic.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

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
    void 상한은_올려_주지_않는다() {
        assertThat(FeedbackService.capByLength("IL", words(20))).isEqualTo("IL");
        assertThat(FeedbackService.capByLength("IM1", words(300))).isEqualTo("IM1");
    }

    @Test
    void level이_없거나_이상하면_null_예전_계산으로() {
        assertThat(FeedbackService.capByLength(null, words(100))).isNull();
        assertThat(FeedbackService.capByLength("NH", words(100))).isNull();
        assertThat(FeedbackService.capByLength("", words(100))).isNull();
    }

    @Test
    void 운영_프롬프트는_실험에서_잰_level_v1과_같다() throws Exception {
        String measured = Files.readString(Path.of("docs/performance/2026-10-07-grading-calibration/level-v1.md")).stripTrailing();
        assertThat(GroqService.LEVEL_PROMPT).isEqualTo(measured);
    }
}
