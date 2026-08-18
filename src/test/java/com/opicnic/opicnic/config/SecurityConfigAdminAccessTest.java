package com.opicnic.opicnic.config;

import com.opicnic.opicnic.controller.AdminController;
import com.opicnic.opicnic.controller.AdminQuestionSetApiController;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.service.CustomOAuth2UserService;
import com.opicnic.opicnic.service.QuestionAssemblyService;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SEC-01 회귀 테스트: /admin/**, /api/admin/**는 로그인 여부가 아니라 실제 ADMIN authority를 요구해야 한다.
@WebMvcTest(controllers = {AdminController.class, AdminQuestionSetApiController.class})
@Import(SecurityConfig.class)
class SecurityConfigAdminAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QuestionSetRepository questionSetRepository;
    @MockBean
    private QuestionAssemblyService questionAssemblyService;
    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;
    @MockBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockBean
    private MemberRepository memberRepository;
    @MockBean
    private SurveyProfileRepository surveyProfileRepository;
    // WebConfig(WebMvcConfigurer)가 @WebMvcTest에 자동 포함되면서 RateLimitInterceptor -> PracticeAttemptService
    // 의존성 체인이 함께 로드되므로 목으로 채워줘야 한다.
    @MockBean
    private PracticeAttemptService practiceAttemptService;
    @MockBean
    private RateLimiterService rateLimiterService;

    @Test
    @WithAnonymousUser
    void anonymousIsRedirectedNotShownAdminView() throws Exception {
        mockMvc.perform(get("/admin/question-sets"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(authorities = "USER")
    void regularUserIsForbiddenFromAdminView() throws Exception {
        mockMvc.perform(get("/admin/question-sets"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessAdminView() throws Exception {
        when(questionSetRepository.findAll()).thenReturn(List.of());

        // admin/question-sets.html이 포함하는 header 프래그먼트가 #authentication.principal.attributes를
        // 참조하므로(OAuth2User 모양 가정), @WithMockUser의 일반 UserDetails principal로는 SpEL이 깨진다.
        // 실제 로그인 흐름과 같은 모양의 OAuth2User/OAuth2AuthenticationToken을 직접 구성한다.
        OAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ADMIN")),
                Map.of("id", "admin-test-id", "provider", "kakao", "providerId", "admin-test-id"),
                "id"
        );
        var token = new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(
                principal, principal.getAuthorities(), "kakao");

        mockMvc.perform(get("/admin/question-sets").with(authentication(token)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "USER")
    void regularUserIsForbiddenFromAdminApiMutation() throws Exception {
        mockMvc.perform(post("/api/admin/question-sets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\",\"topic\":\"JOGGING\"}"))
                .andExpect(status().isForbidden());
    }
}
