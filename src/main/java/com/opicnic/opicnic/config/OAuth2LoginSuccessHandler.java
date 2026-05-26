package com.opicnic.opicnic.config;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final SurveyProfileRepository surveyProfileRepository;
    private final MemberRepository memberRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User user = (OAuth2User) authentication.getPrincipal();
        String providerId = user.getName();
        String provider = user.getAttributes().get("provider").toString();

        Member member = memberRepository.findByProviderAndProviderId(provider, providerId).orElseThrow();
        boolean hasProfile = surveyProfileRepository.findByMemberId(member.getId()).isPresent();

        getRedirectStrategy().sendRedirect(request, response, hasProfile ? "/" : "/onboarding");
    }
}
