package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.ExamSchedule;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.SurveyProfile;
import com.opicnic.opicnic.domain.SurveyProfile.TargetGrade;
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.repository.ExamScheduleRepository;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.service.ExamPlanService;
import com.opicnic.opicnic.service.OpicComboPatternProvider;
import com.opicnic.opicnic.service.ExamPlanService.ComboStat;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// PC-04 회귀 테스트: 시험 계획의 "약한 콤보 추천"이 현재 난이도에서 실제로 시작할 수 없는
// category(예: LEVEL_4에서 C5)를 추천 링크로 내보내지 않아야 한다.
class ExamControllerWeakComboFilterTest {

    @Test
    void startableWeakCombosExcludesUnsupportedCategoryAtLowDifficulty() {
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        FeedbackResultRepository feedbackResultRepository = Mockito.mock(FeedbackResultRepository.class);
        ExamScheduleRepository examScheduleRepository = Mockito.mock(ExamScheduleRepository.class);
        SurveyProfileRepository surveyProfileRepository = Mockito.mock(SurveyProfileRepository.class);
        ExamPlanService examPlanService = new ExamPlanService();

        ExamController controller = new ExamController(
                memberRepository, feedbackResultRepository, examScheduleRepository,
                surveyProfileRepository, examPlanService, new OpicComboPatternProvider());

        OAuth2User user = Mockito.mock(OAuth2User.class);
        when(user.getAttributes()).thenReturn(Map.of("provider", "kakao"));
        when(user.getName()).thenReturn("provider-id-1");

        Member member = Member.builder().id(1L).build();
        when(memberRepository.findByProviderAndProviderId("kakao", "provider-id-1")).thenReturn(Optional.of(member));
        when(feedbackResultRepository.findByMemberIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        SurveyProfile profile = SurveyProfile.builder().member(member)
                .preferredDifficulty(SurveyDifficulty.LEVEL_4).build();
        when(surveyProfileRepository.findByMemberId(1L)).thenReturn(Optional.of(profile));

        ExamSchedule schedule = ExamSchedule.builder()
                .id(1L).member(member).examDate(LocalDate.now().plusDays(30))
                .targetGrade(TargetGrade.IH).dailyMinutes(60).studyDaysPerWeek(5).build();
        when(examScheduleRepository.findTopByMemberIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(schedule));

        Model model = new ExtendedModelMap();
        controller.examPage(user, model);

        @SuppressWarnings("unchecked")
        List<ComboStat> startable = (List<ComboStat>) model.getAttribute("startableWeakCombos");
        assertThat(startable).isNotNull();
        assertThat(startable.stream().map(ComboStat::category)).doesNotContain("C5");
        assertThat(startable.stream().map(ComboStat::category)).contains("C1", "C2", "C3", "C4");
    }
}
