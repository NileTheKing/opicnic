package com.opicnic.opicnic.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.config.RateLimiterService;
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
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// 자기소개(questionType=null)는 실제 시험에서도 채점 문항으로 취급되지 않는다.
// finalize가 자기소개를 FeedbackResult로 DB에 저장하지 않아야, "총 문항 수"/"최근 기록"/
// "코칭 열람 조건" 같은 문항 개수 기반 통계에 섞이지 않는다.
class PracticeAttemptApiControllerFinalizeTest {

    @Test
    void finalizeDoesNotPersistSelfIntroduction() {
        PracticeAttemptService attemptService = Mockito.mock(PracticeAttemptService.class);
        FeedbackService feedbackService = Mockito.mock(FeedbackService.class);
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        FeedbackResultRepository feedbackResultRepository = Mockito.mock(FeedbackResultRepository.class);
        FeedbackTagRepository feedbackTagRepository = Mockito.mock(FeedbackTagRepository.class);

        PracticeAttemptApiController controller = new PracticeAttemptApiController(
                attemptService, feedbackService, memberRepository,
                feedbackResultRepository, feedbackTagRepository, new ObjectMapper(),
                new RateLimiterService());

        String attemptId = "attempt-1";
        PracticeAttempt attempt = new PracticeAttempt(
                attemptId, java.util.Arrays.asList(null, 10L), 1L, PracticeMode.MOCK_EXAM,
                null, null, Instant.now().plusSeconds(3600), AttemptStatus.IN_PROGRESS);
        when(attemptService.requireValidAttempt(attemptId)).thenReturn(attempt);

        QuestionDto selfIntro = new QuestionDto(null, "Please introduce yourself.", "자기소개", null);
        QuestionDto graded = new QuestionDto(10L, "content", "topic", QuestionType.TYPE_1);

        FeedbackDTO selfIntroResult = FeedbackDTO.builder()
                .question(selfIntro).sttText("hi").overall("자기소개는 채점 대상이 아닙니다.").build();
        FeedbackDTO gradedResult = FeedbackDTO.builder()
                .question(graded).sttText("answer").overallGrade("IM2")
                .mainPointScore(3).expressionScore(3).accuracyScore(3).fluencyScore(3).contentScore(3)
                .build();

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("practiceFeedbackResults:" + attemptId,
                new java.util.HashMap<>(Map.of(0, selfIntroResult, 1, gradedResult)));

        OAuth2User user = Mockito.mock(OAuth2User.class);
        when(user.getAttribute("provider")).thenReturn("kakao");
        when(user.getAttribute("providerId")).thenReturn("provider-id-1");
        Member member = Member.builder().id(1L).build();
        when(memberRepository.findByProviderAndProviderId("kakao", "provider-id-1")).thenReturn(Optional.of(member));

        when(feedbackResultRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        controller.finalize(attemptId, session, user);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FeedbackResult>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(feedbackResultRepository).saveAll(captor.capture());

        List<FeedbackResult> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getQuestionType()).isEqualTo(QuestionType.TYPE_1);
    }
}
