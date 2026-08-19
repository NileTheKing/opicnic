package com.opicnic.opicnic.service.attempt;

import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.AttemptStatus;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.FeedbackTagRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// PC-01 회귀 테스트(FeedbackPersistenceService로 이동): 자기소개(questionType=null)는
// 실제 시험에서도 채점 문항으로 취급되지 않는다. DB에 저장하지 않아야 "총 문항 수"/
// "최근 기록"/"코칭 열람 조건" 같은 문항 개수 기반 통계에 섞이지 않는다.
class FeedbackPersistenceServiceTest {

    @Test
    void selfIntroductionIsExcludedFromPersistedFeedback() {
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        FeedbackResultRepository feedbackResultRepository = Mockito.mock(FeedbackResultRepository.class);
        FeedbackTagRepository feedbackTagRepository = Mockito.mock(FeedbackTagRepository.class);
        FeedbackPersistenceService service = new FeedbackPersistenceService(
                memberRepository, feedbackResultRepository, feedbackTagRepository);

        Member member = Member.builder().id(1L).build();
        OAuth2User user = Mockito.mock(OAuth2User.class);
        when(user.getAttribute("provider")).thenReturn("kakao");
        when(user.getAttribute("providerId")).thenReturn("provider-id-1");
        when(memberRepository.findByProviderAndProviderId("kakao", "provider-id-1")).thenReturn(Optional.of(member));
        when(feedbackResultRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        QuestionDto selfIntro = new QuestionDto(null, "Please introduce yourself.", "자기소개", null);
        QuestionDto graded = new QuestionDto(10L, "content", "topic", QuestionType.TYPE_1);
        FeedbackDTO selfIntroResult = FeedbackDTO.builder().question(selfIntro).sttText("hi").build();
        FeedbackDTO gradedResult = FeedbackDTO.builder()
                .question(graded).sttText("answer").overallGrade("IM2")
                .mainPointScore(3).expressionScore(3).accuracyScore(3).fluencyScore(3).contentScore(3)
                .build();

        PracticeAttempt attempt = new PracticeAttempt("attempt-1", java.util.Arrays.asList(null, 10L), 1L,
                PracticeMode.MOCK_EXAM, null, null, Instant.now().plusSeconds(3600), AttemptStatus.IN_PROGRESS);

        service.saveFeedbackResults(List.of(selfIntroResult, gradedResult), user, attempt);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FeedbackResult>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(feedbackResultRepository).saveAll(captor.capture());

        List<FeedbackResult> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getQuestionType()).isEqualTo(QuestionType.TYPE_1);
    }
}
