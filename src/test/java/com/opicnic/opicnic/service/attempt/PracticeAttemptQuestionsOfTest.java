package com.opicnic.opicnic.service.attempt;

import com.opicnic.opicnic.domain.Question;
import com.opicnic.opicnic.domain.QuestionSet;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.AttemptStatus;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.QuestionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// 시도 화면은 저장된 문제 id로 문제를 다시 읽는다. 모의고사의 자기소개는 id가 null이고 DB에 없다.
class PracticeAttemptQuestionsOfTest {

    @Test
    void 자기소개를_포함해_저장된_순서대로_돌려준다() {
        QuestionRepository repository = Mockito.mock(QuestionRepository.class);
        QuestionSet set = Mockito.mock(QuestionSet.class);
        when(set.getTopic()).thenReturn(SurveyTopic.PARK_GOING);
        Question question = Mockito.mock(Question.class);
        when(question.getId()).thenReturn(7L);
        when(question.getContent()).thenReturn("q7");
        when(question.getQuestionSet()).thenReturn(set);
        when(repository.findWithQuestionSetById(7L)).thenReturn(Optional.of(question));
        PracticeAttemptService service = new PracticeAttemptService(Mockito.mock(PracticeAttemptStore.class), repository);

        PracticeAttempt attempt = new PracticeAttempt("a", Arrays.asList(null, 7L), 1L, PracticeMode.MOCK_EXAM,
                null, null, Instant.now().plusSeconds(60), AttemptStatus.IN_PROGRESS);

        List<QuestionDto> questions = service.questionsOf(attempt);

        assertThat(questions).extracting(QuestionDto::getId).containsExactly(null, 7L);
        assertThat(questions.get(0).getTopic()).isEqualTo("자기소개");
        assertThat(questions.get(1).getContent()).isEqualTo("q7");
    }
}
