package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.config.OAuth2LoginSuccessHandler;
import com.opicnic.opicnic.config.RateLimiterService;
import com.opicnic.opicnic.config.SecurityConfig;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.enums.Role;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.service.CustomOAuth2UserService;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import com.opicnic.opicnic.service.guest.GuestProperties;
import com.opicnic.opicnic.service.guest.GuestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GuestLoginController.class)
@Import({SecurityConfig.class, GuestProperties.class, GuestService.class})
class GuestLoginControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired GuestProperties properties;

    @MockBean CustomOAuth2UserService customOAuth2UserService;
    @MockBean OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockBean MemberRepository memberRepository;
    @MockBean SurveyProfileRepository surveyProfileRepository;
    @MockBean PracticeAttemptService practiceAttemptService;
    @MockBean RateLimiterService rateLimiterService;
    @MockBean com.opicnic.opicnic.service.guest.GuestSampleCopier sampleCopier;

    private void stubSave() {
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> {
            Member m = inv.getArgument(0);
            m.setId(42L);
            return m;
        });
    }

    @Test
    void 기능이_꺼져_있으면_404이고_회원도_만들지_않는다() throws Exception {
        properties.setEnabled(false);

        mockMvc.perform(post("/auth/guest").with(csrf())).andExpect(status().isNotFound());

        verify(memberRepository, never()).save(any());
    }

    @Test
    void CSRF_토큰_없이는_거절된다() throws Exception {
        properties.setEnabled(true);

        mockMvc.perform(post("/auth/guest")).andExpect(status().isForbidden());
    }

    @Test
    void 켜져_있으면_게스트를_만들고_세션에_OAuth2User_인증을_심는다() throws Exception {
        properties.setEnabled(true);
        stubSave();
        when(surveyProfileRepository.findByMemberId(42L)).thenReturn(Optional.empty());

        MvcResult result = mockMvc.perform(post("/auth/guest").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/onboarding"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        SecurityContext context = (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(context).isNotNull();
        OAuth2User principal = (OAuth2User) context.getAuthentication().getPrincipal();
        assertThat((String) principal.getAttribute("provider")).isEqualTo("guest");
        assertThat(principal.getName()).isEqualTo(principal.getAttribute("providerId"));
        assertThat(context.getAuthentication().getAuthorities()).extracting(Object::toString).containsExactly(Role.USER.name());
    }

    @Test
    void 게스트는_관리자_화면에_들어갈_수_없다() throws Exception {
        properties.setEnabled(true);
        stubSave();
        when(surveyProfileRepository.findByMemberId(42L)).thenReturn(Optional.empty());
        MvcResult login = mockMvc.perform(post("/auth/guest").with(csrf())).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/admin/question-sets").session(session))
                .andExpect(status().isForbidden());
    }
}
