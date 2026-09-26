package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.SurveyProfile;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.GroqService;
import com.opicnic.opicnic.service.MockExamService;
import com.opicnic.opicnic.service.MockProvider;
import com.opicnic.opicnic.service.STTService;
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
    private final MockExamService mockExamService;
    private final STTService sttService;
    private final GroqService groqService;

    // 측정 스크립트는 로그인 세션이 없어 CSRF 토큰도 없다. 프로덕션 경로(/api/scoring-jobs 등)는 CSRF를 그대로
    // 강제해야 하므로(SEC-06), 여기서 토큰을 미리 발급받아 이후 POST에 실어 보내게 한다.
    @GetMapping("/csrf")
    public Map<String, String> csrfToken(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    // 실패 주입률을 실행 중에 바꾼다(STT·LLM 같은 값). 서킷 튜닝용 "제공자 다운"(1.0)과 대시보드 시연
    // ("평소 → 장애 → 복구")을 앱 재시작 없이 — 재시작하면 지표가 끊기고 워커 회수까지 섞인다.
    // 예) curl -X POST -H "$HEADER: $TOKEN" -b jar '.../mock-failures?rate429=0.3&rate5xx=0.1&rateTimeout=0.05'
    @PostMapping("/mock-failures")
    public Map<String, Double> setMockFailures(@RequestParam(defaultValue = "0") double rate429,
                                               @RequestParam(defaultValue = "0") double rate5xx,
                                               @RequestParam(defaultValue = "0") double rateTimeout) {
        sttService.setMockFailureRates(rate429, rate5xx, rateTimeout);
        groqService.setMockFailureRates(rate429, rate5xx, rateTimeout);
        log.warn("[DEV] mock 실패 주입률 변경: 429={} 5xx={} timeout={}", rate429, rate5xx, rateTimeout);
        return Map.of("rate429", rate429, "rate5xx", rate5xx, "rateTimeout", rateTimeout);
    }

    // 부하에 반응하는 가짜 제공자(MockProvider). 재시도 폭주 실험(scripts/retry-storm.sh)용 — 확률 주입과 달리 몰려서 부를수록 429가 는다.
    // down=true면 모든 호출 즉시 503, limitPerSecond>0이면 STT·채점·태깅 합쳐 초당 그만큼만 받는다.
    @PostMapping("/mock-provider")
    public Map<String, Object> setMockProvider(@RequestParam(defaultValue = "false") boolean down,
                                               @RequestParam(defaultValue = "0") int limitPerSecond) {
        MockProvider.configure(down, limitPerSecond);
        log.warn("[DEV] mock 제공자 변경: down={} limitPerSecond={}", down, limitPerSecond);
        return Map.of("down", down, "limitPerSecond", limitPerSecond);
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

    // S1(모의고사 15문항) 측정용. 실제 흐름(HomeController./practice/mock)은 로그인한 회원의
    // SurveyProfile로 MockExamService.createMockExam()을 부르는데, dev 부하테스트엔 로그인 세션이
    // 없으므로 온보딩 기본값 수준의 고정 프로필로 대신한다(영속화하지 않음, memberId=null과 동일한 이유).
    @PostMapping("/start-mock")
    public ResponseEntity<?> startMockAttempt() {
        SurveyProfile profile = SurveyProfile.builder()
                .preferredDifficulty(SurveyDifficulty.LEVEL_4)
                .selectedTopics(List.of())
                .build();
        List<QuestionDto> questions = mockExamService.createMockExam(profile);
        var attempt = attemptService.createAttempt(
                questions, null, PracticeMode.MOCK_EXAM, null, null);

        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) indexes.add(i);

        return ResponseEntity.ok(Map.of(
                "attemptId", attempt.attemptId(),
                "questionIndexes", indexes,
                "questionCount", questions.size()
        ));
    }
}
