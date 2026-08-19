package com.opicnic.opicnic.service.attempt;

import com.opicnic.opicnic.domain.Question;
import com.opicnic.opicnic.domain.QuestionSet;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.AttemptStatus;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.QuestionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// TEST-02 부하테스트로 실제 발견된 회귀 버그: questionCache가 Question 엔티티를 그대로 캐싱하면,
// 그 엔티티를 로드한 요청의 영속성 컨텍스트가 끝난 뒤 다른 요청이 캐시 히트로 재사용할 때
// LAZY 연관관계(questionSet)를 건드리다 LazyInitializationException이 났다(실서버 재현 확인).
// PracticeAttemptService는 이제 엔티티가 아니라 QuestionDto(문자열로 이미 풀린 topic)를 캐싱해야
// 하며, 이는 캐시 히트 시 엔티티/프록시를 다시 건드리지 않는다는 뜻이다.
class PracticeAttemptServiceQuestionCacheTest {

    @Test
    void cacheHitDoesNotTouchLazyAssociationAgain() {
        PracticeAttemptStore store = Mockito.mock(PracticeAttemptStore.class);
        QuestionRepository questionRepository = Mockito.mock(QuestionRepository.class);
        PracticeAttemptService service = new PracticeAttemptService(store, questionRepository);

        String attemptId = "attempt-1";
        PracticeAttempt attempt = new PracticeAttempt(attemptId, List.of(1L), null, PracticeMode.COMBO,
                null, null, Instant.now().plusSeconds(3600), AttemptStatus.IN_PROGRESS);
        when(store.findById(attemptId)).thenReturn(Optional.of(attempt));

        // 실제로는 LAZY 프록시지만, 이 mock으로 "영속성 컨텍스트가 끝난 뒤 다시 건드리면
        // LazyInitializationException이 난다"는 상황을 흉내낸다. QuestionDto.from()은 캐시 미스
        // 시(첫 호출) getTopic()을 두 번 부르므로(label/name), 그 두 번까지는 정상 반환하고
        // 그 이후(캐시 히트 경로가 다시 건드리면) 예외를 던지도록 해서 회귀를 감지한다.
        QuestionSet questionSet = Mockito.mock(QuestionSet.class);
        when(questionSet.getTopic())
                .thenReturn(SurveyTopic.MOVIE_WATCHING, SurveyTopic.MOVIE_WATCHING)
                .thenThrow(new org.hibernate.LazyInitializationException("no session"));

        Question question = new Question("내용", QuestionType.TYPE_1, questionSet);
        question.setId(1L);
        when(questionRepository.findAllById(List.of(1L))).thenReturn(List.of(question));

        // 캐시 미스 — DB에서 로드하고 DTO로 변환하며 이때 getTopic()이 처음(유일하게) 호출된다.
        List<QuestionDto> first = service.restoreQuestionsForIndexes(attemptId, List.of(0));
        assertThat(first).hasSize(1);
        assertThat(first.get(0).getSurveyTopicName()).isEqualTo("MOVIE_WATCHING");

        // 캐시 히트 — 이미 저장된 QuestionDto만 반환해야 하며, questionSet.getTopic()을
        // 다시 호출하면 안 된다(호출하면 위 stub이 예외를 던져 테스트가 실패한다).
        List<QuestionDto> second = service.restoreQuestionsForIndexes(attemptId, List.of(0));
        assertThat(second).hasSize(1);
        assertThat(second.get(0).getSurveyTopicName()).isEqualTo("MOVIE_WATCHING");

        verify(questionSet, times(2)).getTopic();
        verify(questionRepository, times(1)).findAllById(any());
    }
}
