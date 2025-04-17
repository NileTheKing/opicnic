package com.opicnic.opicnic.config;

import com.opicnic.opicnic.service.CustomOAuth2UserService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        init();

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(new AntPathRequestMatcher("/", "GET")).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/css/**", "GET")).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/js/**", "GET")).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/auth/**", "GET")).permitAll()  // "/auth/**" 경로 허용
                        .requestMatchers(new AntPathRequestMatcher("/images/**", "GET")).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/auth/login")
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService) // CustomOAuth2UserService 빈 주입 확인
                        )
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/auth/login?error")
                )
                .logout(logout -> logout
                        .logoutUrl("/auth/logout") // 여기를 원하는 주소로 설정
                        .logoutSuccessUrl("/")     // 로그아웃 후 이동할 URL
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )
                .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @PostConstruct
    public void init() {
        System.out.println("SecurityConfig loaded. CustomOAuth2UserService: " + customOAuth2UserService);
    }
}