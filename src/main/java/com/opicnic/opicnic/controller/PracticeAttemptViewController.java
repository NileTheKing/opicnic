package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.AttemptStatus;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import com.opicnic.opicnic.service.guest.GuestQuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

// 시도 하나의 문제 화면. 시작(POST /practice/attempts?mode=...)이 시도를 만들고 303으로 여기로 보낸다 —
// 읽기만 하므로 새로고침해도 문제가 바뀌거나 시도가 새로 쌓이지 않는다.
@Controller
@RequiredArgsConstructor
public class PracticeAttemptViewController {

    private final PracticeAttemptService attemptService;
    private final MemberRepository memberRepository;
    private final GuestQuotaService guestQuotaService;

    @GetMapping("/practice/attempts/{attemptId}")
    public String attempt(@PathVariable String attemptId,
                          @AuthenticationPrincipal OAuth2User user,
                          Model model) {
        Long memberId = memberRepository.findByProviderAndProviderId(user.getAttribute("provider"), user.getName())
                .map(Member::getId).orElse(null);
        PracticeAttempt attempt = attemptService.findAttempt(attemptId).orElse(null);
        if (attempt == null || memberId == null || !memberId.equals(attempt.memberId()) || attempt.isExpired()) {
            return "redirect:/?invalidPractice=true";
        }
        // 제출한 시도로 돌아오면(뒤로 가기) 다시 녹음시키지 않고 결과로 보낸다. 잡 id = attemptId
        if (attempt.status() == AttemptStatus.SUBMITTED || attempt.status() == AttemptStatus.FINALIZING) {
            return "redirect:/practice/result/" + attemptId;
        }
        List<?> questions = attemptService.questionsOf(attempt);
        model.addAttribute("questions", questions);
        model.addAttribute("attemptId", attemptId);
        guestQuotaService.addStartAttributes(memberId, questions.size(), model);
        return "practice/question";
    }
}
