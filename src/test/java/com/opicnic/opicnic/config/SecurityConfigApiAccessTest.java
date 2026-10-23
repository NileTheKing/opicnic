package com.opicnic.opicnic.config;

import com.opicnic.opicnic.controller.EnumController;
import com.opicnic.opicnic.controller.ScoringJobApiController;
import com.opicnic.opicnic.controller.ScoringJobViewController;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.enums.Role;
import com.opicnic.opicnic.domain.job.ScoringJob;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.service.CustomOAuth2UserService;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import com.opicnic.opicnic.service.job.ScoringJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// /api/**를 통째로 열고 컨트롤러가 각자 막던 구조를 "기본 로그인 필수"로 뒤집은 회귀 테스트.
// 운영(prod)에선 익명 요청이 컨트롤러에 닿기 전에 막히고(API는 401 JSON, 화면은 로그인 화면),
// 로그인했어도 남의 잡은 컨트롤러의 소유권 검사로 막힌다. 측정 스크립트용 익명 통로는 dev에만 있다.
class SecurityConfigApiAccessTest {

    static final Member OWNER = Member.builder().id(1L).provider("kakao").providerId("owner").role(Role.USER).build();
    static final Member OTHER = Member.builder().id(2L).provider("kakao").providerId("other").role(Role.USER).build();
    static final Member DEV_TESTER = Member.builder().id(3L).provider("dev").providerId("dev-tester").role(Role.USER).build();

    static OAuth2AuthenticationToken login(Member m) {
        var principal = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("USER")),
                Map.of("provider", m.getProvider(), "providerId", m.getProviderId()), "providerId");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), m.getProvider());
    }

    @WebMvcTest(controllers = {ScoringJobApiController.class, ScoringJobViewController.class, EnumController.class})
    @Import(SecurityConfig.class)
    abstract static class Base {
        @Autowired MockMvc mockMvc;

        @MockBean ScoringJobService scoringJobService;
        @MockBean PracticeAttemptService practiceAttemptService;
        @MockBean MemberRepository memberRepository;
        @MockBean FeedbackResultRepository feedbackResultRepository;
        @MockBean CustomOAuth2UserService customOAuth2UserService;
        @MockBean OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
        @MockBean SurveyProfileRepository surveyProfileRepository;
        // WebConfig가 같이 뜨면서 RateLimitInterceptor가 필요로 한다
        @MockBean RateLimiterService rateLimiterService;

        @BeforeEach
        void stubMembers() {
            for (Member m : List.of(OWNER, OTHER, DEV_TESTER)) {
                when(memberRepository.findById(m.getId())).thenReturn(Optional.of(m));
                when(memberRepository.findByProviderAndProviderId(m.getProvider(), m.getProviderId())).thenReturn(Optional.of(m));
            }
            when(scoringJobService.find("job-1")).thenReturn(Optional.of(new ScoringJob("job-1", OWNER, PracticeMode.COMBO)));
            when(scoringJobService.find("dev-job")).thenReturn(Optional.of(new ScoringJob("dev-job", DEV_TESTER, PracticeMode.COMBO)));
        }
    }

    @Nested
    @ActiveProfiles("prod")
    class Prod extends Base {

        @Test
        void 익명_API_요청은_컨트롤러에_닿기_전에_401_JSON() throws Exception {
            mockMvc.perform(get("/api/scoring-jobs/job-1"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("로그인이 필요해요."));
        }

        @Test
        void 측정용_dev_테스터_잡도_운영에선_익명으로_못_본다() throws Exception {
            mockMvc.perform(get("/api/scoring-jobs/dev-job")).andExpect(status().isUnauthorized());
        }

        @Test
        void 익명_결과_화면은_로그인_화면으로() throws Exception {
            mockMvc.perform(get("/practice/result/job-1"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/auth/login"));
        }

        @Test
        void 공개_API는_익명도_열린다() throws Exception {
            mockMvc.perform(get("/api/enums/survey-topics")).andExpect(status().isOk());
        }

        @Test
        void 남의_잡_상태는_403() throws Exception {
            mockMvc.perform(get("/api/scoring-jobs/job-1").with(authentication(login(OTHER))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 남의_결과_화면은_열리지_않고_홈으로() throws Exception {
            mockMvc.perform(get("/practice/result/job-1").with(authentication(login(OTHER))))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));
        }

        @Test
        void 내_잡_상태는_200() throws Exception {
            mockMvc.perform(get("/api/scoring-jobs/job-1").with(authentication(login(OWNER))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("job-1"));
        }
    }

    @Nested
    @ActiveProfiles("dev")
    class Dev extends Base {

        @Test
        void 측정_스크립트는_로그인_없이_dev_테스터_잡을_폴링한다() throws Exception {
            mockMvc.perform(get("/api/scoring-jobs/dev-job")).andExpect(status().isOk());
        }

        @Test
        void dev에서도_남의_잡은_익명이면_컨트롤러가_401() throws Exception {
            mockMvc.perform(get("/api/scoring-jobs/job-1")).andExpect(status().isUnauthorized());
        }
    }
}
