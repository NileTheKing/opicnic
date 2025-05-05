package com.opicnic.opicnic.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@Slf4j
public class AuthController {

    @GetMapping("/auth/login")
    public String login() {

        log.info("카카오 로그인 페이지 호출");
        return "/auth/login";
    }
    @GetMapping("/test-auth")
    public String testAuthentication(Model model, Authentication authentication) {
        log.info("테스트 호출");
        if (authentication != null) {
            System.out.println("로그인됨: " + authentication.getName());
            model.addAttribute("nickname", authentication.getName());
        } else {
            System.out.println("인증 없음");
        }
        return "/auth/test";
    }
}
