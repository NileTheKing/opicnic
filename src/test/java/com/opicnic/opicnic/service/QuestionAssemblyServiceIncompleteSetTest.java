package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.Question;
import com.opicnic.opicnic.domain.QuestionSet;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.mockito.Mockito;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// ADMIN-02 회귀 테스트: 관리자가 정상 CRUD로 문제 없는(또는 이 콤보에 필요한 타입이 빠진) 세트를
// 만들어도, 그 세트가 무작위로 뽑히는 순간 출제가 실패해선 안 된다 — 같은 topic에 정상 세트가
// 하나라도 있으면 무작위 추첨에서 항상 그 세트만 후보가 돼야 한다.
class QuestionAssemblyServiceIncompleteSetTest {

    private QuestionSet emptySet(String name, SurveyTopic topic) {
        return new QuestionSet(name, topic);
    }

    private QuestionSet completeSet(String name, SurveyTopic topic) {
        QuestionSet set = new QuestionSet(name, topic);
        set.getQuestions().add(new Question("질문1", QuestionType.TYPE_1, set));
        set.getQuestions().add(new Question("질문2", QuestionType.TYPE_2, set));
        return set;
    }

    @RepeatedTest(20)
    void emptySetNeverBreaksAssembleWhenAValidSetExists() {
        QuestionSetRepository repository = Mockito.mock(QuestionSetRepository.class);
        QuestionAssemblyService service = new QuestionAssemblyService(repository, new Random());

        QuestionSet empty = emptySet("빈 세트", SurveyTopic.JOGGING);
        QuestionSet complete = completeSet("정상 세트", SurveyTopic.JOGGING);
        when(repository.findByTopicWithDetails(SurveyTopic.JOGGING))
                .thenReturn(List.of(empty, complete));

        ComboPattern pattern = new ComboPattern("테스트 콤보", List.of(QuestionType.TYPE_1, QuestionType.TYPE_2));

        // 빈 세트가 먼저 리스트에 있어도(무작위 추첨 순서와 무관하게) 항상 정상 세트에서만 뽑혀야 한다.
        List<QuestionDto> questions = service.assemble(SurveyTopic.JOGGING, pattern);
        assertThat(questions).hasSize(2);
    }

    @Test
    void throwsClearlyWhenNoSetHasAllRequiredTypes() {
        QuestionSetRepository repository = Mockito.mock(QuestionSetRepository.class);
        QuestionAssemblyService service = new QuestionAssemblyService(repository, new Random());

        QuestionSet empty = emptySet("빈 세트", SurveyTopic.JOGGING);
        when(repository.findByTopicWithDetails(SurveyTopic.JOGGING)).thenReturn(List.of(empty));

        ComboPattern pattern = new ComboPattern("테스트 콤보", List.of(QuestionType.TYPE_1));

        assertThatThrownBy(() -> service.assemble(SurveyTopic.JOGGING, pattern))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
