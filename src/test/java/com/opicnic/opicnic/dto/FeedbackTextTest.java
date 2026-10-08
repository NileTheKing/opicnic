package com.opicnic.opicnic.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackTextTest {

    @Test
    void 진단과_예시를_나눈다() {
        var fix = FeedbackText.fix("Feeling과 Why가 빠졌습니다.\n예) 'It was relaxing.' -> 'It was so relaxing because it's quiet.'");
        assertThat(fix.diagnosis()).isEqualTo("Feeling과 Why가 빠졌습니다.");
        assertThat(fix.before()).isEqualTo("It was relaxing.");
        assertThat(fix.after()).isEqualTo("It was so relaxing because it's quiet.");
        assertThat(fix.hasExample()).isTrue();
    }

    @Test
    void 따옴표가_든_문장도_끝까지_자른다() {
        var fix = FeedbackText.fix("진단\n예) 'I'm there.' -> 'I'm always there.'");
        assertThat(fix.before()).isEqualTo("I'm there.");
        assertThat(fix.after()).isEqualTo("I'm always there.");
    }

    @Test
    void 예시가_없으면_전체가_진단() {
        var fix = FeedbackText.fix("문법 오류가 거의 없습니다.");
        assertThat(fix.diagnosis()).isEqualTo("문법 오류가 거의 없습니다.");
        assertThat(fix.hasExample()).isFalse();
    }

    @Test
    void 진단_없이_예시만_있어도_된다() {
        var fix = FeedbackText.fix("예) 'a b.' -> 'c d.'");
        assertThat(fix.diagnosis()).isNull();
        assertThat(fix.hasExample()).isTrue();
    }

    @Test
    void 바꾼_문장에서_새로_들어간_단어만_표시() {
        var segs = FeedbackText.highlight("It was really relaxing.", "It was so relaxing, because the waves were calm.");
        assertThat(segs).extracting(FeedbackText.Segment::text)
                .containsExactly("It was ", "so ", "relaxing, ", "because the waves were calm.");
        assertThat(segs).extracting(FeedbackText.Segment::added).containsExactly(false, true, false, true);
    }

    @Test
    void 표시를_이어붙이면_바꾼_문장_그대로() {
        String after = "My favorite cafe — honestly — is near my school, and I feel so relaxed there.";
        var segs = FeedbackText.highlight("My favorite cafe is near my school.", after);
        assertThat(segs.stream().map(FeedbackText.Segment::text).reduce("", String::concat)).isEqualTo(after);
    }

    @Test
    void 모범답변을_세_문장씩_문단으로() {
        var p = FeedbackText.paragraphs("One. Two! Three? Four. Five.");
        assertThat(p).containsExactly("One. Two! Three?", "Four. Five.");
    }

    @Test
    void 소수점이나_약어_뒤_소문자에서는_안_자른다() {
        assertThat(FeedbackText.paragraphs("It costs 3.5 dollars. e.g. coffee is nice.")).hasSize(1);
    }
}
