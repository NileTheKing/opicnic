package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.SurveyProfile;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.service.SurveyTopicPolicy;
import com.opicnic.opicnic.service.TopicCatalog;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// PC-21 회귀 테스트: 온보딩을 두 탭에서 완료해도 두 번째 요청이 500이 아니라
// 홈으로 조용히 리다이렉트되어야 한다 (순차 중복 + 진짜 동시 요청 모두).
class OnboardingControllerDuplicateSubmitTest {

    // PC-11 정책(12개 이상 + 그룹별 최소)을 만족하는 유효한 주제 12개
    private static final List<com.opicnic.opicnic.domain.enums.SurveyTopic> VALID_TOPICS = List.of(
            com.opicnic.opicnic.domain.enums.SurveyTopic.MOVIE_WATCHING,
            com.opicnic.opicnic.domain.enums.SurveyTopic.TV_WATCHING,
            com.opicnic.opicnic.domain.enums.SurveyTopic.PERFORMANCE_WATCHING,
            com.opicnic.opicnic.domain.enums.SurveyTopic.CONCERT_WATCHING,
            com.opicnic.opicnic.domain.enums.SurveyTopic.PARK_GOING,
            com.opicnic.opicnic.domain.enums.SurveyTopic.BEACH_GOING,
            com.opicnic.opicnic.domain.enums.SurveyTopic.MUSIC_LISTENING,
            com.opicnic.opicnic.domain.enums.SurveyTopic.READING,
            com.opicnic.opicnic.domain.enums.SurveyTopic.NO_EXERCISE,
            com.opicnic.opicnic.domain.enums.SurveyTopic.WALKING,
            com.opicnic.opicnic.domain.enums.SurveyTopic.STAYCATION,
            com.opicnic.opicnic.domain.enums.SurveyTopic.DOMESTIC_TRAVEL
    );

    private OnboardingController newController(MemberRepository memberRepository,
                                                 SurveyProfileRepository surveyProfileRepository) {
        return new OnboardingController(memberRepository, surveyProfileRepository, new TopicCatalog(), new SurveyTopicPolicy());
    }

    private OAuth2User mockUser() {
        OAuth2User user = Mockito.mock(OAuth2User.class);
        when(user.getName()).thenReturn("provider-id-1");
        when(user.getAttributes()).thenReturn(Map.of("provider", "kakao"));
        return user;
    }

    @Test
    void sequentialDuplicateSubmitRedirectsInsteadOfInserting() {
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        SurveyProfileRepository surveyProfileRepository = Mockito.mock(SurveyProfileRepository.class);
        OnboardingController controller = newController(memberRepository, surveyProfileRepository);

        Member member = Member.builder().id(1L).build();
        when(memberRepository.findByProviderAndProviderId("kakao", "provider-id-1")).thenReturn(Optional.of(member));
        when(surveyProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(SurveyProfile.builder().member(member).build()));

        String result = controller.completeOnboarding(null, SurveyProfile.ResidenceType.WITH_FAMILY,
                null, null, VALID_TOPICS, mockUser());

        assertThat(result).isEqualTo("redirect:/");
        verify(surveyProfileRepository, never()).save(any());
    }

    @Test
    void concurrentSubmitSwallowsUniqueConstraintViolation() {
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        SurveyProfileRepository surveyProfileRepository = Mockito.mock(SurveyProfileRepository.class);
        OnboardingController controller = newController(memberRepository, surveyProfileRepository);

        Member member = Member.builder().id(1L).build();
        when(memberRepository.findByProviderAndProviderId("kakao", "provider-id-1")).thenReturn(Optional.of(member));
        // 둘 다 존재 체크를 통과 (진짜 동시성)
        when(surveyProfileRepository.findByMemberId(1L)).thenReturn(Optional.empty());
        when(surveyProfileRepository.save(any())).thenThrow(new DataIntegrityViolationException("unique constraint"));

        String result = controller.completeOnboarding(null, SurveyProfile.ResidenceType.WITH_FAMILY,
                null, null, VALID_TOPICS, mockUser());

        assertThat(result).isEqualTo("redirect:/");
        verify(surveyProfileRepository, times(1)).save(any());
    }

    @Test
    void completingWithFewerThanMinimumTopicsIsRejected() {
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        SurveyProfileRepository surveyProfileRepository = Mockito.mock(SurveyProfileRepository.class);
        OnboardingController controller = newController(memberRepository, surveyProfileRepository);

        Member member = Member.builder().id(1L).build();
        when(memberRepository.findByProviderAndProviderId("kakao", "provider-id-1")).thenReturn(Optional.of(member));
        when(surveyProfileRepository.findByMemberId(1L)).thenReturn(Optional.empty());

        String result = controller.completeOnboarding(null, SurveyProfile.ResidenceType.WITH_FAMILY,
                null, null, List.of(com.opicnic.opicnic.domain.enums.SurveyTopic.PARK_GOING), mockUser());

        assertThat(result).isEqualTo("redirect:/onboarding/topics?error=invalidTopics");
        verify(surveyProfileRepository, never()).save(any());
    }
}
