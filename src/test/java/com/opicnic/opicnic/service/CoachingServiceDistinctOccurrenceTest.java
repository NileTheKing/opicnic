package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.FeedbackTag;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.repository.CoachingReportRepository;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.FeedbackTagRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// FU-06 회귀 테스트: CoachingService의 요소별/유형별 집계는 FeedbackTag row 개수가 아니라
// "서로 다른 몇 개의 답변(FeedbackResult)에서 나왔는지"로 세야 한다. addTags()가 답변 하나 안의
// 중복은 이제 막지만, 이 방어가 없으면 DB에 이미 남아있는 중복 row(과거 데이터, 버그, 수동 조작)만으로도
// 답변 하나가 MIN_PATTERN_COUNT(3)를 혼자 채워 "반복 패턴"으로 잘못 보고될 수 있다.
class CoachingServiceDistinctOccurrenceTest {

    private CoachingService newService() {
        return new CoachingService(
                Mockito.mock(GroqService.class),
                Mockito.mock(FeedbackResultRepository.class),
                Mockito.mock(FeedbackTagRepository.class),
                Mockito.mock(CoachingReportRepository.class),
                new ExamPlanService(),
                Mockito.mock(SurveyProfileRepository.class),
                new ObjectMapper());
    }

    private FeedbackResult resultWithId(long id) {
        return FeedbackResult.builder().id(id).questionType(QuestionType.TYPE_1).build();
    }

    private FeedbackTag tagFor(FeedbackResult result, String category, String tag) {
        return FeedbackTag.builder().feedbackResult(result).category(category).tag(tag).build();
    }

    @SuppressWarnings("unchecked")
    private Object invokeBuildElementSections(CoachingService service, Map<Long, FeedbackResult> resultById,
                                               List<FeedbackTag> tags) throws Exception {
        Method m = CoachingService.class.getDeclaredMethod("buildElementSections", Map.class, List.class);
        m.setAccessible(true);
        return m.invoke(service, resultById, tags);
    }

    private Map<String, String> byElementOf(Object elementSections) throws Exception {
        Method m = elementSections.getClass().getDeclaredMethod("byElement");
        m.setAccessible(true);
        return (Map<String, String>) m.invoke(elementSections);
    }

    @Test
    void oneAnswerWithDuplicateTagRowsDoesNotAloneFormAPattern() throws Exception {
        CoachingService service = newService();
        FeedbackResult single = resultWithId(1L);
        Map<Long, FeedbackResult> resultById = new HashMap<>(Map.of(1L, single));

        // 같은 답변(id=1)에 대해 DB에 TENSE_ERROR row가 5개 남아있는 상황(과거 버그/수동 데이터)을 흉내낸다.
        List<FeedbackTag> tags = List.of(
                tagFor(single, "accuracy", "TENSE_ERROR"),
                tagFor(single, "accuracy", "TENSE_ERROR"),
                tagFor(single, "accuracy", "TENSE_ERROR"),
                tagFor(single, "accuracy", "TENSE_ERROR"),
                tagFor(single, "accuracy", "TENSE_ERROR")
        );

        Object sections = invokeBuildElementSections(service, resultById, tags);
        Map<String, String> byElement = byElementOf(sections);

        // 답변이 하나뿐이므로(occurrence=1 < MIN_PATTERN_COUNT=3) "정확성" 패턴이 나오면 안 된다.
        assertThat(byElement).doesNotContainKey("정확성");
    }

    @Test
    void threeDistinctAnswersWithSameTagFormsAPattern() throws Exception {
        CoachingService service = newService();
        FeedbackResult r1 = resultWithId(1L);
        FeedbackResult r2 = resultWithId(2L);
        FeedbackResult r3 = resultWithId(3L);
        Map<Long, FeedbackResult> resultById = new HashMap<>(Map.of(1L, r1, 2L, r2, 3L, r3));

        List<FeedbackTag> tags = List.of(
                tagFor(r1, "accuracy", "TENSE_ERROR"),
                tagFor(r2, "accuracy", "TENSE_ERROR"),
                tagFor(r3, "accuracy", "TENSE_ERROR")
        );

        Object sections = invokeBuildElementSections(service, resultById, tags);
        Map<String, String> byElement = byElementOf(sections);

        assertThat(byElement).containsKey("정확성");
        assertThat(byElement.get("정확성")).contains("TENSE_ERROR: 3건");
    }

    @Test
    void duplicateRowsAcrossFewAnswersDoNotInflateOccurrenceBeyondDistinctAnswerCount() throws Exception {
        CoachingService service = newService();
        FeedbackResult r1 = resultWithId(1L);
        FeedbackResult r2 = resultWithId(2L);
        Map<Long, FeedbackResult> resultById = new HashMap<>(Map.of(1L, r1, 2L, r2));

        // 답변 2개뿐인데 각각 중복 row가 있어 총 row 수는 6개 -> 예전 로직이면 6건으로 잘못 집계됐다.
        List<FeedbackTag> tags = List.of(
                tagFor(r1, "accuracy", "TENSE_ERROR"),
                tagFor(r1, "accuracy", "TENSE_ERROR"),
                tagFor(r1, "accuracy", "TENSE_ERROR"),
                tagFor(r2, "accuracy", "TENSE_ERROR"),
                tagFor(r2, "accuracy", "TENSE_ERROR"),
                tagFor(r2, "accuracy", "TENSE_ERROR")
        );

        Object sections = invokeBuildElementSections(service, resultById, tags);
        Map<String, String> byElement = byElementOf(sections);

        // 서로 다른 답변은 2개뿐이라(< MIN_PATTERN_COUNT=3) 여전히 패턴으로 보고되면 안 된다.
        assertThat(byElement).doesNotContainKey("정확성");
    }
}
