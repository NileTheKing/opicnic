package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.AttemptStatus;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import com.opicnic.opicnic.service.guest.GuestQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.ui.ExtendedModelMap;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 시도 화면은 읽기만 한다: 새로고침해도 같은 문제이고, 남의 시도나 끝난 시도는 문제를 보여 주지 않는다.
class PracticeAttemptViewControllerTest {

    private final PracticeAttemptService attemptService = Mockito.mock(PracticeAttemptService.class);
    private final MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
    private final PracticeAttemptViewController controller = new PracticeAttemptViewController(
            attemptService, memberRepository, Mockito.mock(GuestQuotaService.class));
    private final OAuth2User user = Mockito.mock(OAuth2User.class);

    @BeforeEach
    void login() {
        when(user.getAttribute("provider")).thenReturn("kakao");
        when(user.getName()).thenReturn("pid");
        when(memberRepository.findByProviderAndProviderId("kakao", "pid"))
                .thenReturn(Optional.of(Member.builder().id(1L).build()));
    }

    private PracticeAttempt attempt(Long owner, AttemptStatus status, Instant expiresAt) {
        PracticeAttempt attempt = new PracticeAttempt("a1", List.of(7L), owner, PracticeMode.COMBO, null, null, expiresAt, status);
        when(attemptService.findAttempt("a1")).thenReturn(Optional.of(attempt));
        return attempt;
    }

    private Instant later() {
        return Instant.now().plusSeconds(3600);
    }

    @Test
    void 진행_중인_내_시도는_저장된_문제를_다시_만들지_않고_보여_준다() {
        PracticeAttempt attempt = attempt(1L, AttemptStatus.IN_PROGRESS, later());
        List<QuestionDto> questions = List.of(new QuestionDto(7L, "q", "t", null));
        when(attemptService.questionsOf(attempt)).thenReturn(questions);
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.attempt("a1", user, model)).isEqualTo("practice/question");
        assertThat(model.get("questions")).isEqualTo(questions);
        assertThat(model.get("attemptId")).isEqualTo("a1");
        verify(attemptService, never()).createAttempt(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void 남의_시도는_문제를_보여_주지_않는다() {
        attempt(2L, AttemptStatus.IN_PROGRESS, later());
        assertThat(controller.attempt("a1", user, new ExtendedModelMap())).isEqualTo("redirect:/?invalidPractice=true");
        verify(attemptService, never()).questionsOf(Mockito.any());
    }

    @Test
    void 없거나_만료된_시도는_홈으로_보낸다() {
        when(attemptService.findAttempt("a1")).thenReturn(Optional.empty());
        assertThat(controller.attempt("a1", user, new ExtendedModelMap())).isEqualTo("redirect:/?invalidPractice=true");

        attempt(1L, AttemptStatus.IN_PROGRESS, Instant.now().minusSeconds(1));
        assertThat(controller.attempt("a1", user, new ExtendedModelMap())).isEqualTo("redirect:/?invalidPractice=true");
    }

    @Test
    void 제출한_시도는_결과로_보낸다() {
        attempt(1L, AttemptStatus.SUBMITTED, later());
        assertThat(controller.attempt("a1", user, new ExtendedModelMap())).isEqualTo("redirect:/practice/result/a1");
    }
}
