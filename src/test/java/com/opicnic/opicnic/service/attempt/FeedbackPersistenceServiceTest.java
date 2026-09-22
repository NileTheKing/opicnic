package com.opicnic.opicnic.service.attempt;

import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.job.ScoringJob;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.FeedbackTagRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// PC-01 회귀 테스트: 자기소개(questionType=null)는
// 실제 시험에서도 채점 문항으로 취급되지 않는다. DB에 저장하지 않아야 "총 문항 수"/
// "최근 기록"/"코칭 열람 조건" 같은 문항 개수 기반 통계에 섞이지 않는다.
class FeedbackPersistenceServiceTest {

    private final FeedbackResultRepository feedbackResultRepository = Mockito.mock(FeedbackResultRepository.class);
    private final FeedbackTagRepository feedbackTagRepository = Mockito.mock(FeedbackTagRepository.class);
    private final FeedbackPersistenceService service = new FeedbackPersistenceService(feedbackResultRepository, feedbackTagRepository);
    private final Member member = Member.builder().id(1L).build();

    @Test
    void selfIntroductionIsNotPersisted() {
        ScoringJob job = new ScoringJob("attempt-1", member, PracticeMode.MOCK_EXAM);
        QuestionDto selfIntro = new QuestionDto(null, "Please introduce yourself.", "자기소개", null);
        FeedbackDTO selfIntroResult = FeedbackDTO.builder().question(selfIntro).sttText("hi").build();

        FeedbackResult saved = service.saveOne(selfIntroResult, job);

        assertThat(saved).isNull();
        Mockito.verifyNoInteractions(feedbackResultRepository, feedbackTagRepository);
    }

    // 콤보 패턴/카테고리는 잡 행에서 결과 행으로 그대로 복사돼야 학습분석(콤보↔유형 사이클)이 성립한다
    @Test
    void gradedFeedbackIsPersistedWithComboMetadataFromJob() {
        when(feedbackResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ScoringJob job = new ScoringJob("attempt-2", member, PracticeMode.COMBO, "TYPE_1,TYPE_5", "C4");
        QuestionDto graded = new QuestionDto(10L, "content", "topic", QuestionType.TYPE_1);
        FeedbackDTO gradedResult = FeedbackDTO.builder()
                .question(graded).sttText("answer").overallGrade("IM2")
                .mainPointScore(3).expressionScore(3).accuracyScore(3).fluencyScore(3).contentScore(3)
                .build();

        FeedbackResult saved = service.saveOne(gradedResult, job);

        ArgumentCaptor<FeedbackResult> captor = ArgumentCaptor.forClass(FeedbackResult.class);
        Mockito.verify(feedbackResultRepository).save(captor.capture());
        assertThat(saved).isSameAs(captor.getValue());
        assertThat(saved.getQuestionType()).isEqualTo(QuestionType.TYPE_1);
        assertThat(saved.getAttemptId()).isEqualTo("attempt-2");
        assertThat(saved.getComboPatternKey()).isEqualTo("TYPE_1,TYPE_5");
        assertThat(saved.getComboCategory()).isEqualTo("C4");
        assertThat(saved.getMember()).isSameAs(member);
    }
}
