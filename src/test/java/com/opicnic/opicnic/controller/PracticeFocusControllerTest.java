package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.SurveyProfile;
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.service.OpicComboPatternProvider;
import com.opicnic.opicnic.service.TopicCatalog;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.ui.Model;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// PC-04 회귀 테스트: 집중 연습 "콤보별" 탭이 현재 난이도에 없는 category(C4 또는 C5)를
// 눌리는 버튼으로 계속 보여주지 않도록, 화면에 실제 지원 category 집합을 넘겨야 한다.
class PracticeFocusControllerTest {

    private PracticeFocusController newController(MemberRepository memberRepository,
                                                    SurveyProfileRepository surveyProfileRepository,
                                                    QuestionSetRepository questionSetRepository) {
        return new PracticeFocusController(memberRepository, surveyProfileRepository,
                questionSetRepository, new TopicCatalog(), new OpicComboPatternProvider());
    }

    private OAuth2User mockUser() {
        OAuth2User user = Mockito.mock(OAuth2User.class);
        when(user.getAttribute("provider")).thenReturn("kakao");
        when(user.getName()).thenReturn("provider-id-1");
        return user;
    }

    @Test
    void lowDifficultyExcludesC5FromSupportedCategories() {
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        SurveyProfileRepository surveyProfileRepository = Mockito.mock(SurveyProfileRepository.class);
        QuestionSetRepository questionSetRepository = Mockito.mock(QuestionSetRepository.class);
        PracticeFocusController controller = newController(memberRepository, surveyProfileRepository, questionSetRepository);

        Member member = Member.builder().id(1L).build();
        when(memberRepository.findByProviderAndProviderId("kakao", "provider-id-1")).thenReturn(Optional.of(member));
        SurveyProfile profile = SurveyProfile.builder().member(member)
                .preferredDifficulty(SurveyDifficulty.LEVEL_4).build();
        when(surveyProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));
        when(questionSetRepository.findExistingTopics(any())).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        controller.focusPage(mockUser(), model);

        @SuppressWarnings("unchecked")
        Set<String> supported = (Set<String>) model.getAttribute("supportedComboCategories");
        assertThat(supported).contains("C1", "C2", "C3", "C4").doesNotContain("C5");
    }

    @Test
    void highDifficultyExcludesC4FromSupportedCategories() {
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        SurveyProfileRepository surveyProfileRepository = Mockito.mock(SurveyProfileRepository.class);
        QuestionSetRepository questionSetRepository = Mockito.mock(QuestionSetRepository.class);
        PracticeFocusController controller = newController(memberRepository, surveyProfileRepository, questionSetRepository);

        Member member = Member.builder().id(1L).build();
        when(memberRepository.findByProviderAndProviderId("kakao", "provider-id-1")).thenReturn(Optional.of(member));
        SurveyProfile profile = SurveyProfile.builder().member(member)
                .preferredDifficulty(SurveyDifficulty.LEVEL_5).build();
        when(surveyProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));
        when(questionSetRepository.findExistingTopics(any())).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        controller.focusPage(mockUser(), model);

        @SuppressWarnings("unchecked")
        Set<String> supported = (Set<String>) model.getAttribute("supportedComboCategories");
        assertThat(supported).contains("C1", "C2", "C3", "C5").doesNotContain("C4");
    }
}
