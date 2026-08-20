package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// AI-01 회귀 테스트: LLM 응답도 신뢰 경계 밖 입력이다. 점수 필드가 1~5 범위를 벗어나거나
// (예: score=99) 파싱이 안 되면, 조용히 그 값을 그대로 저장하거나(통계 오염) null을 int로
// 언박싱하다 NPE로 요청 전체가 깨지는 대신 — 이미 있는 재시도 루프가 "품질 실패"로 취급해
// 재시도하고, 끝내 실패하면 failed=true 카드로 반환해야 한다.
class FeedbackServiceScoreValidationTest {

    private FeedbackService newService(GroqService groqService, STTService sttService) {
        return new FeedbackService(Mockito.mock(ComboPracticeService.class), sttService, groqService, new ObjectMapper());
    }

    private String emptyTagsJson() {
        return "{\"mainPoint\":[],\"expression\":{\"vocab\":[],\"sentence\":[],\"imagery\":[]},\"accuracy\":[],\"content\":[]}";
    }

    @Test
    void outOfRangeScoreEndsAsFailedCardNotSilentlyStored() {
        STTService sttService = Mockito.mock(STTService.class);
        GroqService groqService = Mockito.mock(GroqService.class);
        FeedbackService feedbackService = newService(groqService, sttService);

        when(sttService.sendStreamToStt(any(), any())).thenReturn(
                "This is a sufficiently long answer so that it passes the minimum word count guard for STT text.");
        // expressionScore=99는 1~5 범위 밖 — score()가 예외를 던져야 하고, 재시도를 다 써도
        // LLM mock이 계속 같은 값을 주므로 결국 실패 카드로 귀결되어야 한다.
        Map<String, Object> badFeedback = Map.of(
                "mainPoint", "양호", "mainPointScore", 3,
                "expression", "양호", "expressionScore", 99,
                "accuracy", "양호", "accuracyScore", 3,
                "content", "양호", "contentScore", 3
        );
        when(groqService.getOpicFeedback(any(), any())).thenReturn(badFeedback);
        when(groqService.extractFeedbackTags(any(), any(), any(), any(), any())).thenReturn(emptyTagsJson());

        QuestionDto question = new QuestionDto(1L, "content", "topic", QuestionType.TYPE_1);
        List<InputStream> streams = List.of(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        List<FeedbackDTO> results = feedbackService.getComboFeedbackStreaming(streams, List.of(question));

        FeedbackDTO result = results.get(0);
        assertThat(result.isFailed()).isTrue();
        assertThat(result.getExpressionScore()).isNull();
    }

    @Test
    void missingScoreFieldEndsAsFailedCardNotNpe() {
        STTService sttService = Mockito.mock(STTService.class);
        GroqService groqService = Mockito.mock(GroqService.class);
        FeedbackService feedbackService = newService(groqService, sttService);

        when(sttService.sendStreamToStt(any(), any())).thenReturn(
                "This is a sufficiently long answer so that it passes the minimum word count guard for STT text.");
        // contentScore 필드 자체가 없음 — 예전 코드는 score()가 null을 반환하고
        // "int ctScore = score(...)"에서 언박싱하다 NPE가 나서 재시도조차 못 하고 그 시도가 죽었다.
        Map<String, Object> missingFieldFeedback = Map.of(
                "mainPoint", "양호", "mainPointScore", 3,
                "expression", "양호", "expressionScore", 3,
                "accuracy", "양호", "accuracyScore", 3
        );
        when(groqService.getOpicFeedback(any(), any())).thenReturn(missingFieldFeedback);
        when(groqService.extractFeedbackTags(any(), any(), any(), any(), any())).thenReturn(emptyTagsJson());

        QuestionDto question = new QuestionDto(1L, "content", "topic", QuestionType.TYPE_1);
        List<InputStream> streams = List.of(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        List<FeedbackDTO> results = feedbackService.getComboFeedbackStreaming(streams, List.of(question));

        FeedbackDTO result = results.get(0);
        assertThat(result.isFailed()).isTrue();
    }

    @Test
    void overlongOrBlankOrUnknownTagsAreDropped() {
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
        when(groqService.getOpicFeedback(any(), any())).thenReturn(goodFeedback);

        // TYPE_1(groupA)의 mainPoint allowlist는 WHY_MISSING/FEELING_MISSING/MP_LATE/MP_GOOD뿐이다.
        // 200자 가비지, 빈 문자열, allowlist 밖 "정상태그"(오타/환각 흉내)는 전부 버려지고
        // 실제 allowlist에 있는 MP_GOOD만 남아야 한다.
        String overlongTag = "가".repeat(200);
        String tagsJson = "{\"mainPoint\":[\"" + overlongTag + "\", \"\", \"정상태그\", \"MP_GOOD\"],"
                + "\"expression\":{\"vocab\":[],\"sentence\":[],\"imagery\":[]},\"accuracy\":[],\"content\":[]}";
        when(groqService.extractFeedbackTags(any(), any(), any(), any(), any())).thenReturn(tagsJson);

        QuestionDto question = new QuestionDto(1L, "content", "topic", QuestionType.TYPE_1);
        List<InputStream> streams = List.of(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        List<FeedbackDTO> results = feedbackService.getComboFeedbackStreaming(streams, List.of(question));

        FeedbackDTO result = results.get(0);
        assertThat(result.isFailed()).isFalse();
        assertThat(result.getTags()).hasSize(1);
        assertThat(result.getTags().get(0).tag()).isEqualTo("MP_GOOD");
    }

    // REVIEW-09 회귀 테스트: allowlist 밖 태그(환각/오타/스키마 이탈)는 카테고리·유형과 무관하게 저장되면 안 된다.
    @Test
    void tagOutsideAllowlistForGivenCategoryIsDropped() {
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
        when(groqService.getOpicFeedback(any(), any())).thenReturn(goodFeedback);

        // accuracy allowlist엔 없는 "NO_ERROR"(프롬프트가 명시적으로 쓰지 말라고 지시한 값) — 환각 시나리오.
        String tagsJson = "{\"mainPoint\":[],\"expression\":{\"vocab\":[],\"sentence\":[],\"imagery\":[]},"
                + "\"accuracy\":[\"NO_ERROR\", \"TENSE_ERROR\"],\"content\":[]}";
        when(groqService.extractFeedbackTags(any(), any(), any(), any(), any())).thenReturn(tagsJson);

        QuestionDto question = new QuestionDto(1L, "content", "topic", QuestionType.TYPE_1);
        List<InputStream> streams = List.of(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        List<FeedbackDTO> results = feedbackService.getComboFeedbackStreaming(streams, List.of(question));

        FeedbackDTO result = results.get(0);
        assertThat(result.getTags()).extracting(t -> t.tag()).containsExactly("TENSE_ERROR");
    }

    // FU-06 회귀 테스트: allowlist를 통과하는 동일 태그를 반복 반환해도(반복/환각) 답변 하나에는
    // 그 태그가 한 번만 저장돼야 한다. 상한(5개)을 걸어도 "중복 5개"가 그대로 저장되면 이 답변
    // 하나만으로 CoachingService의 MIN_PATTERN_COUNT(3)를 채워버리는 문제가 있었다 — size만 보면
    // 이 결함을 못 잡으므로 남은 태그가 정확히 무엇인지까지 고정한다.
    @Test
    void tagCountPerCategoryIsCapped() {
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
        when(groqService.getOpicFeedback(any(), any())).thenReturn(goodFeedback);

        // accuracy allowlist는 4개뿐이지만 같은 값을 반복해서 6개를 보내는 비정상 응답을 흉내낸다.
        String tagsJson = "{\"mainPoint\":[],\"expression\":{\"vocab\":[],\"sentence\":[],\"imagery\":[]},"
                + "\"accuracy\":[\"TENSE_ERROR\",\"TENSE_ERROR\",\"TENSE_ERROR\",\"TENSE_ERROR\","
                + "\"TENSE_ERROR\",\"TENSE_ERROR\"],\"content\":[]}";
        when(groqService.extractFeedbackTags(any(), any(), any(), any(), any())).thenReturn(tagsJson);

        QuestionDto question = new QuestionDto(1L, "content", "topic", QuestionType.TYPE_1);
        List<InputStream> streams = List.of(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        List<FeedbackDTO> results = feedbackService.getComboFeedbackStreaming(streams, List.of(question));

        FeedbackDTO result = results.get(0);
        assertThat(result.getTags()).extracting(t -> t.tag()).containsExactly("TENSE_ERROR");
    }

    // FU-06 회귀 테스트: 중복/unknown/blank가 섞여 있어도, 실제로 서로 다른 허용 태그는
    // 최초 등장 순서대로 전부 유지돼야 한다(상한 4개 이하면 잘리지 않음).
    @Test
    void distinctAllowedTagsAreAllKeptInFirstSeenOrder() {
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
        when(groqService.getOpicFeedback(any(), any())).thenReturn(goodFeedback);

        // accuracy allowlist 4개(TENSE/ARTICLE/PREPOSITION/SUBJECT_VERB_ERROR) 전부 + 중복 + unknown + blank 섞임.
        String tagsJson = "{\"mainPoint\":[],\"expression\":{\"vocab\":[],\"sentence\":[],\"imagery\":[]},"
                + "\"accuracy\":[\"TENSE_ERROR\",\"ARTICLE_ERROR\",\"TENSE_ERROR\",\"\",\"NO_ERROR\","
                + "\"PREPOSITION_ERROR\",\"SUBJECT_VERB_ERROR\",\"ARTICLE_ERROR\"],\"content\":[]}";
        when(groqService.extractFeedbackTags(any(), any(), any(), any(), any())).thenReturn(tagsJson);

        QuestionDto question = new QuestionDto(1L, "content", "topic", QuestionType.TYPE_1);
        List<InputStream> streams = List.of(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        List<FeedbackDTO> results = feedbackService.getComboFeedbackStreaming(streams, List.of(question));

        FeedbackDTO result = results.get(0);
        assertThat(result.getTags()).extracting(t -> t.tag())
                .containsExactly("TENSE_ERROR", "ARTICLE_ERROR", "PREPOSITION_ERROR", "SUBJECT_VERB_ERROR");
    }
}
