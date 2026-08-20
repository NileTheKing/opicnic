package com.opicnic.opicnic.service;

import java.util.List;
import java.util.Set;

// REVIEW-09: FeedbackService가 GroqService.extractFeedbackTags()의 LLM 응답을 저장 전에 검증할 때 쓰는
// category별 태그 allowlist. GroqService의 프롬프트가 적어주는 "선택 가능 태그" 목록과 값 자체는
// 같아야 한다 — 프롬프트(GroqService.extractFeedbackTags())를 바꾸면 여기도 같이 바꿔야 한다.
// 프롬프트 생성 코드 자체는 이번 변경 범위가 아니라 손대지 않았다.
public final class FeedbackTagVocabulary {

    private FeedbackTagVocabulary() {
    }

    public static final Set<String> EXPRESSION_VOCAB = Set.of("VOCAB_BASIC", "VOCAB_RICH");
    public static final Set<String> EXPRESSION_SENTENCE = Set.of("SENTENCE_SIMPLE", "SENTENCE_VARIED");
    public static final Set<String> EXPRESSION_IMAGERY = Set.of("IMAGERY_FLAT", "IMAGERY_VIVID");
    public static final Set<String> ACCURACY = Set.of(
            "TENSE_ERROR", "ARTICLE_ERROR", "PREPOSITION_ERROR", "SUBJECT_VERB_ERROR");

    private static final List<String> GROUP_A = List.of("TYPE_1", "TYPE_2", "TYPE_3", "TYPE_4", "TYPE_8");
    private static final List<String> GROUP_C = List.of("TYPE_9", "TYPE_10");

    // 롤플레이(TYPE_5~7)는 MP 평가 제외라 태그도 없다 — 빈 allowlist라 무엇이 와도 걸러진다.
    public static Set<String> mainPointOptions(String questionType) {
        if (GROUP_A.contains(questionType)) {
            return Set.of("WHY_MISSING", "FEELING_MISSING", "MP_LATE", "MP_GOOD");
        }
        if (GROUP_C.contains(questionType)) {
            return Set.of("FRAME_UNCLEAR", "FRAME_LATE", "FRAME_GOOD");
        }
        return Set.of();
    }

    public static Set<String> contentOptions(String questionType) {
        if (questionType == null) return Set.of("CONTENT_GOOD");
        return switch (questionType) {
            case "TYPE_1" -> Set.of("DESCRIPTION_SHALLOW", "CONTENT_GOOD");
            case "TYPE_2" -> Set.of("CLUE_MISSING", "CONTENT_GOOD");
            case "TYPE_3" -> Set.of("STORY_STRUCTURE_WEAK", "TIMELINE_UNCLEAR", "CONTENT_GOOD");
            case "TYPE_4" -> Set.of("CLUE_MISSING", "REASON_SHALLOW", "CONTENT_GOOD");
            case "TYPE_5", "TYPE_6" -> Set.of("DIALOGUE_UNNATURAL", "QUESTION_COUNT_SHORT", "CONTENT_GOOD");
            case "TYPE_7" -> Set.of("ALTERNATIVE_LACKING", "CONTENT_GOOD");
            case "TYPE_8" -> Set.of("SITUATION_VAGUE", "RESOLUTION_MISSING", "CONTENT_GOOD");
            case "TYPE_9" -> Set.of("FRAME_MISSING", "ONE_SIDED", "CONTENT_GOOD");
            case "TYPE_10" -> Set.of("OPINION_MISSING", "REASON_LACKING", "CONTENT_GOOD");
            default -> Set.of("CONTENT_GOOD");
        };
    }
}
