package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.Question;
import com.opicnic.opicnic.domain.QuestionSet;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// CACHE-01 회귀 테스트: 관리자 수정 후 evict()를 호출하면 다음 조회가 DB를 다시 읽어야 한다.
// evict 없이는 첫 조회 결과가 프로세스 재시작 전까지 그대로 캐시에 남아있었다.
class QuestionAssemblyServiceCacheTest {

    private QuestionSet setWithQuestion(String name, SurveyTopic topic, QuestionType type, String content) {
        QuestionSet set = new QuestionSet(name, topic);
        set.getQuestions().add(new Question(content, type, set));
        return set;
    }

    @Test
    void evictForcesNextAssembleToReReadFromRepository() {
        QuestionSetRepository repository = Mockito.mock(QuestionSetRepository.class);
        QuestionAssemblyService service = new QuestionAssemblyService(repository, new Random());

        QuestionSet original = setWithQuestion("원본 세트", SurveyTopic.JOGGING, QuestionType.TYPE_1, "원본 질문");
        QuestionSet updated = setWithQuestion("수정된 세트", SurveyTopic.JOGGING, QuestionType.TYPE_1, "수정된 질문");
        when(repository.findByTopicWithDetails(SurveyTopic.JOGGING))
                .thenReturn(List.of(original))
                .thenReturn(List.of(updated));

        QuestionDto first = service.assembleSingle(SurveyTopic.JOGGING, QuestionType.TYPE_1);
        assertThat(first.getContent()).isEqualTo("원본 질문");

        // evict 없이 다시 조회하면 캐시에서 그대로 원본을 반환해야 한다 (캐시 동작 자체 확인)
        QuestionDto cached = service.assembleSingle(SurveyTopic.JOGGING, QuestionType.TYPE_1);
        assertThat(cached.getContent()).isEqualTo("원본 질문");
        verify(repository, times(1)).findByTopicWithDetails(SurveyTopic.JOGGING);

        service.evict(SurveyTopic.JOGGING);

        QuestionDto afterEvict = service.assembleSingle(SurveyTopic.JOGGING, QuestionType.TYPE_1);
        assertThat(afterEvict.getContent()).isEqualTo("수정된 질문");
        verify(repository, times(2)).findByTopicWithDetails(SurveyTopic.JOGGING);
    }
}
