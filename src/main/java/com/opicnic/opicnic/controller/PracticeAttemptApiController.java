package com.opicnic.opicnic.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/practice-attempts")
@RequiredArgsConstructor
@Slf4j
public class PracticeAttemptApiController {

    private final PracticeAttemptService attemptService;
    private final FeedbackService feedbackService;
    private final MemberRepository memberRepository;
    private final FeedbackResultRepository feedbackResultRepository;
    private final ObjectMapper objectMapper;

    // attemptId → (questionIndex → FeedbackDTO)
    private final ConcurrentHashMap<String, Map<Integer, FeedbackDTO>> accumulator = new ConcurrentHashMap<>();

    @PostMapping("/answers")
    public ResponseEntity<?> submitAnswers(HttpServletRequest request,
                                           @AuthenticationPrincipal OAuth2User oAuth2User) {
        return processSubmission(request, oAuth2User);
    }

    @PostMapping("/answers/retry")
    public ResponseEntity<?> retryAnswers(HttpServletRequest request,
                                          @AuthenticationPrincipal OAuth2User oAuth2User) {
        return processSubmission(request, oAuth2User);
    }

    @PostMapping("/answers/finalize")
    public ResponseEntity<?> finalize(@RequestParam String attemptId,
                                      HttpSession session,
                                      @AuthenticationPrincipal OAuth2User oAuth2User) {
        attemptService.requireValidAttempt(attemptId);
        attemptService.consume(attemptId);

        Map<Integer, FeedbackDTO> results = accumulator.remove(attemptId);
        List<FeedbackDTO> feedbackList = results == null ? List.of() :
                results.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(Map.Entry::getValue)
                        .toList();

        saveFeedbackResults(feedbackList, oAuth2User);
        session.setAttribute("feedbackList", feedbackList);

        Map<String, String> response = new HashMap<>();
        response.put("resultUrl", "/practice/feedback/result");
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<?> processSubmission(HttpServletRequest request, OAuth2User oAuth2User) {
        try {
            String attemptId = request.getParameter("attemptId");
            List<Integer> questionIndexes = objectMapper.readValue(
                    request.getParameter("questionIndexes"), new TypeReference<>() {});

            attemptService.requireValidAttempt(attemptId);
            List<QuestionDto> questions = attemptService.restoreQuestionsForIndexes(attemptId, questionIndexes);

            List<InputStream> streams = new ArrayList<>();
            for (var part : request.getParts()) {
                if ("files".equals(part.getName())) streams.add(part.getInputStream());
            }

            if (streams.size() != questions.size()) {
                return ResponseEntity.badRequest().body("파일 수와 문제 수가 일치하지 않습니다.");
            }

            List<FeedbackDTO> feedbackList = feedbackService.getComboFeedbackStreaming(streams, questions);

            Map<Integer, FeedbackDTO> attemptResults = accumulator.computeIfAbsent(attemptId, k -> new ConcurrentHashMap<>());
            List<Integer> failedIndexes = new ArrayList<>();
            for (int i = 0; i < feedbackList.size(); i++) {
                FeedbackDTO fb = feedbackList.get(i);
                int originalIndex = questionIndexes.get(i);
                if (fb.isFailed()) {
                    failedIndexes.add(originalIndex);
                } else {
                    attemptResults.put(originalIndex, fb);
                }
            }

            List<Map<String, Object>> results = new ArrayList<>();
            for (int i = 0; i < feedbackList.size(); i++) {
                Map<String, Object> item = new HashMap<>();
                item.put("questionIndex", questionIndexes.get(i));
                item.put("feedback", feedbackList.get(i));
                results.add(item);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("results", results);
            response.put("failedIndexes", failedIndexes);
            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            log.warn("유효하지 않은 attempt: {}", e.getMessage());
            return ResponseEntity.status(410).body(e.getMessage());
        } catch (Exception e) {
            log.error("제출 처리 중 오류: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("처리 중 오류가 발생했습니다.");
        }
    }

    private void saveFeedbackResults(List<FeedbackDTO> feedbackList, OAuth2User oAuth2User) {
        if (oAuth2User == null) return;
        String provider = oAuth2User.getAttribute("provider");
        String providerId = oAuth2User.getAttribute("providerId");
        Member member = memberRepository.findByProviderAndProviderId(provider, providerId).orElse(null);
        if (member == null) return;

        List<FeedbackResult> toSave = feedbackList.stream()
                .filter(fb -> !fb.isFailed())
                .map(fb -> FeedbackResult.builder()
                        .member(member)
                        .questionContent(fb.getQuestion().getContent())
                        .sttText(fb.getSttText())
                        .vocabulary(fb.getVocabulary())
                        .grammar(fb.getGrammar())
                        .mainPoint(fb.getMainPoint())
                        .fluency(fb.getFluency())
                        .content(fb.getContent())
                        .overall(fb.getOverall())
                        .improvements(fb.getImprovements())
                        .build())
                .toList();

        feedbackResultRepository.saveAll(toSave);
        log.info("[DB 저장] 피드백 {}건 (member: {})", toSave.size(), member.getId());
    }
}
