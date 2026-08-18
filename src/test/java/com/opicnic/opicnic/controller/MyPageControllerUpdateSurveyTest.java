package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.SurveyProfile;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.NotificationSettingRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.service.SurveyTopicPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// PC-05 회귀 테스트: 마이페이지 저장 시 거주 주제가 사라지거나
// 거주 형태 변경 시 새 거주 주제가 추가되지 않는 문제를 검증한다.
class MyPageControllerUpdateSurveyTest {

    private MyPageController newController(MemberRepository memberRepository,
                                             SurveyProfileRepository surveyProfileRepository) {
        NotificationSettingRepository notificationSettingRepository = Mockito.mock(NotificationSettingRepository.class);
        return new MyPageController(memberRepository, notificationSettingRepository, surveyProfileRepository, new SurveyTopicPolicy());
    }

    private OAuth2User mockUser() {
        OAuth2User user = Mockito.mock(OAuth2User.class);
        when(user.getName()).thenReturn("provider-id-1");
        when(user.getAttributes()).thenReturn(Map.of("provider", "kakao"));
        return user;
    }

    // PC-11 정책(12개 이상 + 그룹별 최소)을 만족하는 유효한 주제 12개
    private static final List<SurveyTopic> VALID_TOPICS = List.of(
            SurveyTopic.MOVIE_WATCHING, SurveyTopic.TV_WATCHING, SurveyTopic.PERFORMANCE_WATCHING,
            SurveyTopic.CONCERT_WATCHING, SurveyTopic.PARK_GOING, SurveyTopic.BEACH_GOING,
            SurveyTopic.MUSIC_LISTENING, SurveyTopic.READING,
            SurveyTopic.NO_EXERCISE, SurveyTopic.WALKING,
            SurveyTopic.STAYCATION, SurveyTopic.DOMESTIC_TRAVEL
    );

    @Test
    void savingWithoutChangesKeepsResidenceTopic() {
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        SurveyProfileRepository surveyProfileRepository = Mockito.mock(SurveyProfileRepository.class);
        MyPageController controller = newController(memberRepository, surveyProfileRepository);

        Member member = Member.builder().id(1L).build();
        when(memberRepository.findByProviderAndProviderId("kakao", "provider-id-1")).thenReturn(Optional.of(member));

        SurveyProfile profile = SurveyProfile.builder().member(member)
                .residenceType(SurveyProfile.ResidenceType.WITH_FAMILY).build();
        profile.getSelectedTopics().add(SurveyTopic.LIVING_WITH_FAMILY);
        profile.getSelectedTopics().addAll(VALID_TOPICS);
        when(surveyProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));
        when(surveyProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        controller.updateSurvey(null, SurveyProfile.ResidenceType.WITH_FAMILY, VALID_TOPICS, mockUser());

        List<SurveyTopic> expected = new java.util.ArrayList<>(VALID_TOPICS);
        expected.add(SurveyTopic.LIVING_WITH_FAMILY);
        assertThat(profile.getSelectedTopics()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void changingResidenceTypeSwapsResidenceTopic() {
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        SurveyProfileRepository surveyProfileRepository = Mockito.mock(SurveyProfileRepository.class);
        MyPageController controller = newController(memberRepository, surveyProfileRepository);

        Member member = Member.builder().id(1L).build();
        when(memberRepository.findByProviderAndProviderId("kakao", "provider-id-1")).thenReturn(Optional.of(member));

        SurveyProfile profile = SurveyProfile.builder().member(member)
                .residenceType(SurveyProfile.ResidenceType.WITH_FAMILY).build();
        profile.getSelectedTopics().add(SurveyTopic.LIVING_WITH_FAMILY);
        profile.getSelectedTopics().addAll(VALID_TOPICS);
        when(surveyProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));
        when(surveyProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        controller.updateSurvey(null, SurveyProfile.ResidenceType.ALONE, VALID_TOPICS, mockUser());

        List<SurveyTopic> expected = new java.util.ArrayList<>(VALID_TOPICS);
        expected.add(SurveyTopic.LIVING_ALONE);
        assertThat(profile.getSelectedTopics()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void savingBelowMinimumTopicsIsRejectedAndProfilePreserved() {
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        SurveyProfileRepository surveyProfileRepository = Mockito.mock(SurveyProfileRepository.class);
        MyPageController controller = newController(memberRepository, surveyProfileRepository);

        Member member = Member.builder().id(1L).build();
        when(memberRepository.findByProviderAndProviderId("kakao", "provider-id-1")).thenReturn(Optional.of(member));

        String result = controller.updateSurvey(null, SurveyProfile.ResidenceType.WITH_FAMILY,
                List.of(SurveyTopic.PARK_GOING), mockUser());

        assertThat(result).isEqualTo("redirect:/mypage?error=invalidTopics");
        Mockito.verifyNoInteractions(surveyProfileRepository);
    }
}
