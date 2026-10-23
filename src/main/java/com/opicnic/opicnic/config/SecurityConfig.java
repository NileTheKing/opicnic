package com.opicnic.opicnic.config;

import com.opicnic.opicnic.service.CustomOAuth2UserService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final Environment environment;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        init();
        // 측정 스크립트(k6)는 로그인 없이 시작·업로드 URL·접수·폴링·결과 화면을 부른다. dev에서만 연다.
        // acceptsProfiles는 활성 프로파일이 없으면 spring.profiles.default(dev)로 판정한다 — RateLimiterService와 같은 기준
        boolean dev = environment.acceptsProfiles(Profiles.of("dev"));

        http
                .authorizeHttpRequests(auth -> {
                    // SEC-01: 일반 USER의 로그인 여부만 보지 않고 ADMIN authority를 실제로 강제한다.
                    // CustomOAuth2UserService가 이미 member.getRole().name()을 authority 문자열로 부여하므로
                    // 여기서 hasAuthority("ADMIN")만 걸면 된다. 뷰 라우트(/admin/**)도 API와 동일하게 보호한다.
                    auth.requestMatchers("/admin/**", "/api/admin/**").hasAuthority("ADMIN");
                    if (dev) {
                        auth.requestMatchers("/api/practice-attempts/**", "/api/scoring-jobs/**", "/practice/result/**", "/dev/**").permitAll();
                    }
                    // 기본은 로그인 필수, 공개할 것만 연다. 이전엔 /api/**를 통째로 열고 컨트롤러가 각자 막았다 —
                    // 새 API에서 검사를 빠뜨리면 그대로 공개되는 구조라 뒤집었다. 소유권(남의 것인가)은 여전히 컨트롤러가 본다
                    auth.requestMatchers("/", "/auth/**", "/api/enums/**", "/actuator/**", "/css/**", "/js/**").permitAll();
                    auth.anyRequest().authenticated();
                })
                // 비로그인 API 호출은 401 JSON. 로그인 화면 리다이렉트를 주면 fetch가 따라가 HTML을 200으로 받아 원인을 모른다.
                // 화면 요청은 기존처럼 로그인 화면으로 보낸다
                // 하나만 지정하면 그게 모든 요청의 기본이 되므로 화면 쪽(로그인 화면)도 명시한다
                .exceptionHandling(e -> e
                        .defaultAuthenticationEntryPointFor(apiUnauthorized(), new AntPathRequestMatcher("/api/**"))
                        .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/auth/login"), AnyRequestMatcher.INSTANCE))
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/auth/login")
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService) // CustomOAuth2UserService 빈 주입 확인
                        )
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureUrl("/auth/login?error")
                )
                .logout(logout -> logout
                        .logoutUrl("/auth/logout") // 여기를 원하는 주소로 설정
                        .logoutSuccessUrl("/")     // 로그아웃 후 이동할 URL
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                );
                // SEC-06: 세션 기반 OAuth2 로그인을 쓰면서 CSRF를 전역 비활성화하고 있었다.
                // Thymeleaf th:action 폼은 자동으로 토큰이 실리고, JS fetch() 호출부는 각 템플릿에
                // <meta name="_csrf">를 추가해 헤더로 직접 실어 보내도록 수정했다 — 기본 CSRF 설정 유지.

        return http.build();
    }

    private static AuthenticationEntryPoint apiUnauthorized() {
        return (request, response, ex) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"message\":\"로그인이 필요해요.\"}");
        };
    }

    @PostConstruct
    public void init() {
        System.out.println("SecurityConfig loaded. CustomOAuth2UserService: " + customOAuth2UserService);
    }
}