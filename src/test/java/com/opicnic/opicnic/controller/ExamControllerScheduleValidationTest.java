package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.SurveyProfile.TargetGrade;
import com.opicnic.opicnic.repository.ExamScheduleRepository;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.service.ExamPlanService;
import com.opicnic.opicnic.service.OpicComboPatternProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// API-02 회귀 테스트: 화면은 dailyMinutes={30,60,90,120}, studyDaysPerWeek={3,5,7}만 보여주지만
// 서버가 그 범위를 강제하지 않아 임의 정수(음수, 0, 과도한 값)나 과거 시험일이 그대로 저장되던 문제.
class ExamControllerScheduleValidationTest {

    private ExamController controller;
    private ExamScheduleRepository examScheduleRepository;
    private MemberRepository memberRepository;

    private void setUp() {
        memberRepository = Mockito.mock(MemberRepository.class);
        FeedbackResultRepository feedbackResultRepository = Mockito.mock(FeedbackResultRepository.class);
        examScheduleRepository = Mockito.mock(ExamScheduleRepository.class);
        SurveyProfileRepository surveyProfileRepository = Mockito.mock(SurveyProfileRepository.class);
        ExamPlanService examPlanService = Mockito.mock(ExamPlanService.class);
        OpicComboPatternProvider comboPatternProvider = Mockito.mock(OpicComboPatternProvider.class);
        controller = new ExamController(memberRepository, feedbackResultRepository, examScheduleRepository,
                surveyProfileRepository, examPlanService, comboPatternProvider);
    }

    private OAuth2User mockUser() {
        OAuth2User user = Mockito.mock(OAuth2User.class);
        when(user.getAttributes()).thenReturn(Map.of("provider", "kakao"));
        when(user.getName()).thenReturn("provider-id-1");
        when(memberRepository.findByProviderAndProviderId("kakao", "provider-id-1"))
                .thenReturn(Optional.of(Member.builder().id(1L).build()));
        return user;
    }

    @Test
    void rejectsDailyMinutesOutsideAllowedSet() {
        setUp();
        String result = controller.saveSchedule(mockUser(), LocalDate.now().plusDays(30), TargetGrade.IM2, 999, 5);
        assertThat(result).isEqualTo("redirect:/exam?error=invalidSchedule");
        verify(examScheduleRepository, never()).save(any());
    }

    @Test
    void rejectsNegativeStudyDaysPerWeek() {
        setUp();
        String result = controller.saveSchedule(mockUser(), LocalDate.now().plusDays(30), TargetGrade.IM2, 60, -5);
        assertThat(result).isEqualTo("redirect:/exam?error=invalidSchedule");
        verify(examScheduleRepository, never()).save(any());
    }

    @Test
    void rejectsExamDateInThePast() {
        setUp();
        String result = controller.saveSchedule(mockUser(), LocalDate.now().minusDays(1), TargetGrade.IM2, 60, 5);
        assertThat(result).isEqualTo("redirect:/exam?error=invalidSchedule");
        verify(examScheduleRepository, never()).save(any());
    }

    @Test
    void acceptsValuesFromAllowedSets() {
        setUp();
        String result = controller.saveSchedule(mockUser(), LocalDate.now().plusDays(30), TargetGrade.IM2, 90, 7);
        assertThat(result).isEqualTo("redirect:/exam");
        verify(examScheduleRepository).save(any());
    }
}
