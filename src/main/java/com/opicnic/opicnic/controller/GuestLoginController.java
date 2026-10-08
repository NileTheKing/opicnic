package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.service.guest.GuestCreationThrottle;
import com.opicnic.opicnic.service.guest.GuestProperties;
import com.opicnic.opicnic.service.guest.GuestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

// "로그인 없이 둘러보기". 방문자마다 새 게스트 회원을 만들고 카카오 로그인과 같은 모양의 인증을 세션에 심는다.
// CSRF는 기존 설정 그대로 — 폼이 th:action으로 토큰을 싣는다.
@Controller
@RequiredArgsConstructor
public class GuestLoginController {

    private final GuestProperties properties;
    private final GuestService guestService;
    private final GuestCreationThrottle creationThrottle;
    private final HttpSessionSecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @PostMapping("/auth/guest")
    public String login(HttpServletRequest request, HttpServletResponse response) {
        // 꺼져 있으면 엔드포인트 자체가 없는 것처럼
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        // 이미 로그인한 사람의 세션을 게스트로 덮어쓰지 않는다
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null && current.isAuthenticated() && !(current instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }

        // 같은 IP가 계속 새 게스트를 만들어 개인 한도를 우회하는 걸 막는다. 회원은 만들지 않는다
        if (!creationThrottle.tryAcquire(GuestCreationThrottle.clientIp(request))) {
            return "redirect:/auth/login?guestLimit";
        }

        Member guest = guestService.createGuest();

        // 세션 고정 공격 방지: 이미 세션이 있었다면 로그인 시점에 id를 바꾼다
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(guestService.toAuthentication(guest));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        // 게스트는 설문이 항상 채워져 있다(GuestService.createGuest) — 온보딩 없이 바로 홈
        return "redirect:/";
    }
}
