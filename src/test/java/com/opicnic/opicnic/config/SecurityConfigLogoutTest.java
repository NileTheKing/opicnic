package com.opicnic.opicnic.config;

import com.opicnic.opicnic.controller.AuthController;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.service.CustomOAuth2UserService;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// REVIEW-03 회귀 테스트: SEC-06으로 CSRF를 다시 켜면서 Spring Security의 LogoutFilter가
// GET이 아니라 POST만 "/auth/logout"으로 매치하게 됐다(CSRF 활성 시 기본 매처가 POST 전용).
// 템플릿의 <a href="/auth/logout"> GET 링크는 컨트롤러 매핑도 없어 더 이상 로그아웃되지 않고
// 404로 떨어진다 — POST + CSRF 토큰 폼으로 바꿔야 한다.
@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
class SecurityConfigLogoutTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;
    @MockBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockBean
    private MemberRepository memberRepository;
    @MockBean
    private SurveyProfileRepository surveyProfileRepository;
    @MockBean
    private PracticeAttemptService practiceAttemptService;
    @MockBean
    private RateLimiterService rateLimiterService;

    private org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken loggedInUser() {
        OAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("USER")),
                Map.of("id", "test-id", "provider", "kakao", "providerId", "test-id"),
                "id"
        );
        return new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(
                principal, principal.getAuthorities(), "kakao");
    }

    @Test
    void getLogoutNoLongerLogsOut() throws Exception {
        // 이전엔 GET으로도 로그아웃 필터가 동작했다. CSRF 재활성화 이후 GET은 더 이상
        // 매치되지 않으므로, 매핑도 없는 GET /auth/logout은 404여야 한다(회귀 시 3xx로 바뀜).
        mockMvc.perform(get("/auth/logout").with(authentication(loggedInUser())))
                .andExpect(status().isNotFound());
    }

    @Test
    void postLogoutWithCsrfTokenLogsOutAndRedirectsHome() throws Exception {
        mockMvc.perform(post("/auth/logout").with(authentication(loggedInUser())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void postLogoutWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/auth/logout").with(authentication(loggedInUser())))
                .andExpect(status().isForbidden());
    }
}
