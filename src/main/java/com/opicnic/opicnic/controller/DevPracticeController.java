package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// TEST-02: 로그인 세션 없이(k6 부하테스트 등) attempt를 시작할 수 있는 dev 전용 진입점.
// 실제 서비스 흐름(PracticeComboController 등)은 로그인한 회원 기준으로만 attempt를 만들므로
// 이 엔드포인트는 dev 프로파일에서만 열리고, memberId=null인 attempt를 생성한다.
@RestController
@RequestMapping("/api/practice-attempts")
@RequiredArgsConstructor
@Slf4j
@Profile("dev")
public class DevPracticeController {

    private final PracticeAttemptService attemptService;
    private final FeedbackService feedbackService;

    // k6는 로그인 세션이 없어 CSRF 토큰도 없다. 프로덕션 경로(/answers 등)는 CSRF를 그대로
    // 강제해야 하므로(SEC-06), 여기서 토큰을 미리 발급받아 이후 POST에 실어 보내게 한다.
    @GetMapping("/csrf")
    public Map<String, String> csrfToken(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/start")
    public ResponseEntity<?> startAttempt(@RequestParam String topic,
                                          @RequestParam String difficulty) {
        var combo = feedbackService.getComboQuestions(topic, difficulty);
        var attempt = attemptService.createAttempt(
                combo.questions(), null, PracticeMode.COMBO,
                combo.comboPatternKey(), combo.comboCategory());

        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < combo.questions().size(); i++) indexes.add(i);

        return ResponseEntity.ok(Map.of(
                "attemptId", attempt.attemptId(),
                "questionIndexes", indexes,
                "questionCount", combo.questions().size()
        ));
    }
}
