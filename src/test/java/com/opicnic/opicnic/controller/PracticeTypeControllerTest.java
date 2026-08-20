package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.SurveyProfile;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.AttemptStatus;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.service.QuestionAssemblyService;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// PC-03 회귀 테스트: 유형별 연습은 전체 지원 주제가 아니라
// 사용자가 배경설문에서 선택한 주제 중에서만 후보를 뽑아야 한다.
class PracticeTypeControllerTest {

    @Test
    void typePracticeOnlyUsesMemberSelectedTopics() {
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        QuestionSetRepository questionSetRepository = Mockito.mock(QuestionSetRepository.class);
        SurveyProfileRepository surveyProfileRepository = Mockito.mock(SurveyProfileRepository.class);
        QuestionAssemblyService questionAssemblyService = Mockito.mock(QuestionAssemblyService.class);
        PracticeAttemptService practiceAttemptService = Mockito.mock(PracticeAttemptService.class);

        PracticeTypeController controller = new PracticeTypeController(
                questionAssemblyService, practiceAttemptService, memberRepository,
                questionSetRepository, surveyProfileRepository, new Random());

        OAuth2User user = Mockito.mock(OAuth2User.class);
        when(user.getAttribute("provider")).thenReturn("kakao");
        when(user.getName()).thenReturn("provider-id-1");

        Member member = Member.builder().id(1L).build();
        when(memberRepository.findByProviderAndProviderId("kakao", "provider-id-1"))
                .thenReturn(Optional.of(member));

        SurveyProfile profile = Mockito.mock(SurveyProfile.class);
        when(profile.getSelectedTopics()).thenReturn(List.of(SurveyTopic.PARK_GOING));
        when(surveyProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));
        when(questionSetRepository.findExistingTopics(any())).thenReturn(List.of(SurveyTopic.PARK_GOING));
        when(questionAssemblyService.hasQuestionType(SurveyTopic.PARK_GOING, QuestionType.TYPE_1)).thenReturn(true);
        when(questionAssemblyService.assembleSingle(SurveyTopic.PARK_GOING, QuestionType.TYPE_1))
                .thenReturn(new QuestionDto(1L, "content", "topic", QuestionType.TYPE_1));
        when(practiceAttemptService.createAttempt(any(), any(), any(), any(), any()))
                .thenReturn(new PracticeAttempt("attempt-1", List.of(1L), 1L, PracticeMode.COMBO,
                        null, null, Instant.now().plusSeconds(3600), AttemptStatus.IN_PROGRESS));

        org.springframework.ui.Model model = Mockito.mock(org.springframework.ui.Model.class);
        controller.typePractice("TYPE_1", user, model);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SurveyTopic>> captor = ArgumentCaptor.forClass(List.class);
        verify(questionSetRepository).findExistingTopics(captor.capture());
        assertThat(captor.getValue()).containsExactly(SurveyTopic.PARK_GOING);
    }

    @Test
    void typePracticeRedirectsWhenMemberHasNoSelectedTopics() {
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        QuestionSetRepository questionSetRepository = Mockito.mock(QuestionSetRepository.class);
        SurveyProfileRepository surveyProfileRepository = Mockito.mock(SurveyProfileRepository.class);
        QuestionAssemblyService questionAssemblyService = Mockito.mock(QuestionAssemblyService.class);
        PracticeAttemptService practiceAttemptService = Mockito.mock(PracticeAttemptService.class);

        PracticeTypeController controller = new PracticeTypeController(
                questionAssemblyService, practiceAttemptService, memberRepository,
                questionSetRepository, surveyProfileRepository, new Random());

        OAuth2User user = Mockito.mock(OAuth2User.class);
        when(user.getAttribute("provider")).thenReturn("kakao");
        when(user.getName()).thenReturn("provider-id-1");

        Member member = Member.builder().id(1L).build();
        when(memberRepository.findByProviderAndProviderId("kakao", "provider-id-1"))
                .thenReturn(Optional.of(member));
        when(surveyProfileRepository.findByMemberId(1L)).thenReturn(Optional.empty());

        org.springframework.ui.Model model = Mockito.mock(org.springframework.ui.Model.class);
        String result = controller.typePractice("TYPE_1", user, model);

        assertThat(result).isEqualTo("redirect:/?invalidPractice=true");
        verify(questionSetRepository, never()).findExistingTopics(any());
    }

    // REVIEW-07 회귀 테스트: findExistingTopics는 "QuestionSet이 존재하는지"만 보고 그 세트가
    // 이 questionType을 실제로 갖는지는 보지 않는다. 사용자의 topic 중 하나(PARK_GOING)는 세트는
    // 있지만 TYPE_1이 없는 불완전한 세트뿐이고, 다른 topic(BEACH_GOING)은 완전하다면
    // 불완전한 topic이 뽑혀서 "연습 불가"로 끝나면 안 되고 완전한 topic으로 성공해야 한다.
    @Test
    void topicWithoutRequestedTypeIsExcludedFromCandidatesWhenAnotherTopicHasIt() {
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        QuestionSetRepository questionSetRepository = Mockito.mock(QuestionSetRepository.class);
        SurveyProfileRepository surveyProfileRepository = Mockito.mock(SurveyProfileRepository.class);
        QuestionAssemblyService questionAssemblyService = Mockito.mock(QuestionAssemblyService.class);
        PracticeAttemptService practiceAttemptService = Mockito.mock(PracticeAttemptService.class);

        PracticeTypeController controller = new PracticeTypeController(
                questionAssemblyService, practiceAttemptService, memberRepository,
                questionSetRepository, surveyProfileRepository, new Random());

        OAuth2User user = Mockito.mock(OAuth2User.class);
        when(user.getAttribute("provider")).thenReturn("kakao");
        when(user.getName()).thenReturn("provider-id-1");

        Member member = Member.builder().id(1L).build();
        when(memberRepository.findByProviderAndProviderId("kakao", "provider-id-1"))
                .thenReturn(Optional.of(member));

        SurveyProfile profile = Mockito.mock(SurveyProfile.class);
        when(profile.getSelectedTopics()).thenReturn(List.of(SurveyTopic.PARK_GOING, SurveyTopic.BEACH_GOING));
        when(surveyProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));
        when(questionSetRepository.findExistingTopics(any()))
                .thenReturn(List.of(SurveyTopic.PARK_GOING, SurveyTopic.BEACH_GOING));
        // PARK_GOING: 세트는 있지만 TYPE_1이 없는 불완전한 세트 -> 후보에서 빠져야 함
        when(questionAssemblyService.hasQuestionType(SurveyTopic.PARK_GOING, QuestionType.TYPE_1)).thenReturn(false);
        // BEACH_GOING: 완전한 세트 -> 유일한 후보로 남아야 함
        when(questionAssemblyService.hasQuestionType(SurveyTopic.BEACH_GOING, QuestionType.TYPE_1)).thenReturn(true);
        when(questionAssemblyService.assembleSingle(SurveyTopic.BEACH_GOING, QuestionType.TYPE_1))
                .thenReturn(new QuestionDto(2L, "content", "topic", QuestionType.TYPE_1));
        when(practiceAttemptService.createAttempt(any(), any(), any(), any(), any()))
                .thenReturn(new PracticeAttempt("attempt-1", List.of(2L), 1L, PracticeMode.COMBO,
                        null, null, Instant.now().plusSeconds(3600), AttemptStatus.IN_PROGRESS));

        org.springframework.ui.Model model = Mockito.mock(org.springframework.ui.Model.class);
        String result = controller.typePractice("TYPE_1", user, model);

        assertThat(result).isEqualTo("practice/question");
        verify(questionAssemblyService, never()).assembleSingle(SurveyTopic.PARK_GOING, QuestionType.TYPE_1);
        verify(questionAssemblyService).assembleSingle(SurveyTopic.BEACH_GOING, QuestionType.TYPE_1);
    }
}
