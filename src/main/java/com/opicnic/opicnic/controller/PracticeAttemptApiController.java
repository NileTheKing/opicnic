package com.opicnic.opicnic.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/practice-attempts")
@RequiredArgsConstructor
@Slf4j
public class PracticeAttemptApiController {

    private static final String SESSION_FEEDBACK_RESULTS = "feedbackResults";

    private final PracticeAttemptService attemptService;
    private final FeedbackService feedbackService;
    private final MemberRepository memberRepository;
    private final FeedbackResultRepository feedbackResultRepository;
    private final ObjectMapper objectMapper;

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
        PracticeAttempt attempt = attemptService.requireValidAttempt(attemptId);
        ResponseEntity<?> forbidden = rejectIfNotOwner(attempt, oAuth2User);
        if (forbidden != null) return forbidden;

        int questionCount = attempt.questionIds().size();
        Map<Integer, FeedbackDTO> results = getAttemptResults(session, attemptId);
        if (results.size() != questionCount) {
            return ResponseEntity.badRequest().body("아직 모든 문항의 피드백이 완료되지 않았습니다.");
        }

        List<FeedbackDTO> feedbackResults = results.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();

        saveFeedbackResults(feedbackResults, oAuth2User, attempt);
        attemptService.consume(attemptId);
        removeAttemptResults(session, attemptId);
        session.setAttribute(SESSION_FEEDBACK_RESULTS, feedbackResults);

        Map<String, String> response = new HashMap<>();
        response.put("resultUrl", "/practice/feedback/result");
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<?> processSubmission(HttpServletRequest request, OAuth2User oAuth2User) {
        try {
            String attemptId = request.getParameter("attemptId");
            if (attemptId == null || attemptId.isBlank()) {
                throw new IllegalArgumentException("attemptId는 필수입니다.");
            }
            String questionIndexesParam = request.getParameter("questionIndexes");
            if (questionIndexesParam == null || questionIndexesParam.isBlank()) {
                throw new IllegalArgumentException("questionIndexes는 필수입니다.");
            }
            List<Integer> questionIndexes = objectMapper.readValue(
                    questionIndexesParam, new TypeReference<>() {});
            if (questionIndexes.isEmpty()) {
                throw new IllegalArgumentException("questionIndexes가 비어 있습니다.");
            }

            PracticeAttempt attempt = attemptService.requireValidAttempt(attemptId);
            ResponseEntity<?> forbidden = rejectIfNotOwner(attempt, oAuth2User);
            if (forbidden != null) return forbidden;
            List<QuestionDto> questions = attemptService.restoreQuestionsForIndexes(attemptId, questionIndexes);

            List<InputStream> streams = new ArrayList<>();
            for (var part : request.getParts()) {
                if ("files".equals(part.getName())) streams.add(part.getInputStream());
            }

            if (streams.size() != questions.size()) {
                return ResponseEntity.badRequest().body("파일 수와 문제 수가 일치하지 않습니다.");
            }

            List<FeedbackDTO> submittedFeedbackResults = feedbackService.getComboFeedbackStreaming(streams, questions);

            Map<Integer, FeedbackDTO> attemptResults = getAttemptResults(request.getSession(), attemptId);
            List<Integer> failedIndexes = new ArrayList<>();
            for (int i = 0; i < submittedFeedbackResults.size(); i++) {
                FeedbackDTO fb = submittedFeedbackResults.get(i);
                int originalIndex = questionIndexes.get(i);
                if (fb.isFailed()) {
                    failedIndexes.add(originalIndex);
                } else {
                    attemptResults.put(originalIndex, fb);
                }
            }
            saveAttemptResults(request.getSession(), attemptId, attemptResults);

            List<Map<String, Object>> results = new ArrayList<>();
            for (int i = 0; i < submittedFeedbackResults.size(); i++) {
                Map<String, Object> item = new HashMap<>();
                item.put("questionIndex", questionIndexes.get(i));
                item.put("feedback", submittedFeedbackResults.get(i));
                results.add(item);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("results", results);
            response.put("failedIndexes", failedIndexes);
            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            log.warn("유효하지 않은 attempt: {}", e.getMessage());
            return ResponseEntity.status(410).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("잘못된 제출 요청: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("제출 처리 중 오류: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("처리 중 오류가 발생했습니다.");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, FeedbackDTO> getAttemptResults(HttpSession session, String attemptId) {
        String key = sessionResultKey(attemptId);
        Object value = session.getAttribute(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<Integer, FeedbackDTO>) map;
        }
        return new HashMap<>();
    }

    private void saveAttemptResults(HttpSession session, String attemptId, Map<Integer, FeedbackDTO> results) {
        session.setAttribute(sessionResultKey(attemptId), results);
    }

    private void removeAttemptResults(HttpSession session, String attemptId) {
        session.removeAttribute(sessionResultKey(attemptId));
    }

    private String sessionResultKey(String attemptId) {
        return "practiceFeedbackResults:" + attemptId;
    }

    private ResponseEntity<?> rejectIfNotOwner(PracticeAttempt attempt, OAuth2User oAuth2User) {
        if (attempt.memberId() == null) {
            return null;
        }

        if (oAuth2User == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        Long memberId = findMemberId(oAuth2User);
        if (!attempt.memberId().equals(memberId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("해당 연습 세션에 접근할 수 없습니다.");
        }
        return null;
    }

    private Long findMemberId(OAuth2User oAuth2User) {
        String provider = oAuth2User.getAttribute("provider");
        String providerId = oAuth2User.getAttribute("providerId");
        return memberRepository.findByProviderAndProviderId(provider, providerId)
                .map(Member::getId)
                .orElse(null);
    }

    private void saveFeedbackResults(List<FeedbackDTO> feedbackResults, OAuth2User oAuth2User,
                                      PracticeAttempt attempt) {
        if (oAuth2User == null) return;
        String provider = oAuth2User.getAttribute("provider");
        String providerId = oAuth2User.getAttribute("providerId");
        Member member = memberRepository.findByProviderAndProviderId(provider, providerId).orElse(null);
        if (member == null) return;

        List<FeedbackResult> toSave = feedbackResults.stream()
                .filter(fb -> !fb.isFailed())
                .map(fb -> FeedbackResult.builder()
                        .member(member)
                        .questionId(fb.getQuestion().getId())
                        .questionType(fb.getQuestion().getQuestionType())
                        .surveyTopicName(fb.getQuestion().getSurveyTopicName())
                        .comboPatternKey(attempt.comboPatternKey())
                        .comboCategory(attempt.comboCategory())
                        .questionContent(fb.getQuestion().getContent())
                        .sttText(fb.getSttText())
                        .vocabulary(fb.getVocabulary())
                        .vocabularyScore(fb.getVocabularyScore())
                        .grammar(fb.getGrammar())
                        .grammarScore(fb.getGrammarScore())
                        .mainPoint(fb.getMainPoint())
                        .mainPointScore(fb.getMainPointScore())
                        .fluency(fb.getFluency())
                        .fluencyScore(fb.getFluencyScore())
                        .content(fb.getContent())
                        .contentScore(fb.getContentScore())
                        .overall(fb.getOverall())
                        .overallGrade(fb.getOverallGrade())
                        .improvements(fb.getImprovements())
                        .build())
                .toList();

        feedbackResultRepository.saveAll(toSave);
        log.info("[DB 저장] 피드백 {}건 (member: {}, combo: {})", toSave.size(), member.getId(), attempt.comboCategory());
    }
}
