package com.opicnic.opicnic.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.FeedbackTag;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.dto.ErrorResponse;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.FeedbackTagDto;
import com.opicnic.opicnic.dto.FinalizeResponseDto;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.dto.SubmissionResponseDto;
import com.opicnic.opicnic.dto.SubmissionResultItemDto;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.FeedbackTagRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
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
    private final FeedbackTagRepository feedbackTagRepository;
    private final ObjectMapper objectMapper;

    @PostMapping("/{attemptId}/answers")
    public ResponseEntity<?> submitAnswers(@PathVariable String attemptId,
                                           HttpServletRequest request,
                                           @AuthenticationPrincipal OAuth2User oAuth2User) throws IOException, ServletException {
        return processSubmission(attemptId, request, oAuth2User);
    }

    @PostMapping("/{attemptId}/answers/retry")
    public ResponseEntity<?> retryAnswers(@PathVariable String attemptId,
                                          HttpServletRequest request,
                                          @AuthenticationPrincipal OAuth2User oAuth2User) throws IOException, ServletException {
        return processSubmission(attemptId, request, oAuth2User);
    }

    @PostMapping("/{attemptId}/finalize")
    public ResponseEntity<?> finalize(@PathVariable String attemptId,
                                      HttpSession session,
                                      @AuthenticationPrincipal OAuth2User oAuth2User) {
        PracticeAttempt attempt = attemptService.requireValidAttempt(attemptId);
        ResponseEntity<?> forbidden = rejectIfNotOwner(attempt, oAuth2User);
        if (forbidden != null) return forbidden;

        int questionCount = attempt.questionIds().size();
        Map<Integer, FeedbackDTO> results = getAttemptResults(session, attemptId);
        if (results.size() != questionCount) {
            throw new IllegalArgumentException("아직 모든 문항의 피드백이 완료되지 않았습니다.");
        }

        List<FeedbackDTO> feedbackResults = results.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();

        saveFeedbackResults(feedbackResults, oAuth2User, attempt);
        attemptService.consume(attemptId);
        removeAttemptResults(session, attemptId);
        session.setAttribute(SESSION_FEEDBACK_RESULTS, feedbackResults);

        return ResponseEntity.ok(new FinalizeResponseDto("/practice/feedback/result"));
    }

    // 예외는 여기서 잡지 않고 ApiExceptionHandler(전역 @RestControllerAdvice)로 흘려보낸다.
    private ResponseEntity<?> processSubmission(String attemptId, HttpServletRequest request, OAuth2User oAuth2User) throws IOException, ServletException {
        String questionIndexesParam = request.getParameter("questionIndexes");
        if (questionIndexesParam == null || questionIndexesParam.isBlank()) {
            throw new IllegalArgumentException("questionIndexes는 필수입니다.");
        }
        List<Integer> questionIndexes;
        try {
            questionIndexes = objectMapper.readValue(questionIndexesParam, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("questionIndexes 형식이 올바르지 않습니다.");
        }
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
            throw new IllegalArgumentException("파일 수와 문제 수가 일치하지 않습니다.");
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

        List<SubmissionResultItemDto> results = new ArrayList<>();
        for (int i = 0; i < submittedFeedbackResults.size(); i++) {
            results.add(new SubmissionResultItemDto(questionIndexes.get(i), submittedFeedbackResults.get(i)));
        }

        return ResponseEntity.ok(new SubmissionResponseDto(results, failedIndexes));
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
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("로그인이 필요합니다."));
        }

        Long memberId = findMemberId(oAuth2User);
        if (!attempt.memberId().equals(memberId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("해당 연습 세션에 접근할 수 없습니다."));
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

        List<FeedbackDTO> validFeedback = feedbackResults.stream().filter(fb -> !fb.isFailed()).toList();
        List<FeedbackResult> toSave = validFeedback.stream()
                .map(fb -> FeedbackResult.builder()
                        .member(member)
                        .questionId(fb.getQuestion().getId())
                        .questionType(fb.getQuestion().getQuestionType())
                        .surveyTopicName(fb.getQuestion().getSurveyTopicName())
                        .comboPatternKey(attempt.comboPatternKey())
                        .comboCategory(attempt.comboCategory())
                        .questionContent(fb.getQuestion().getContent())
                        .sttText(fb.getSttText())
                        .expression(fb.getExpression())
                        .expressionScore(fb.getExpressionScore())
                        .expressionQuote(fb.getExpressionQuote())
                        .expressionFix(fb.getExpressionFix())
                        .accuracy(fb.getAccuracy())
                        .accuracyScore(fb.getAccuracyScore())
                        .accuracyQuote(fb.getAccuracyQuote())
                        .accuracyFix(fb.getAccuracyFix())
                        .mainPoint(fb.getMainPoint())
                        .mainPointScore(fb.getMainPointScore())
                        .mainPointQuote(fb.getMainPointQuote())
                        .mainPointFix(fb.getMainPointFix())
                        .fluency(fb.getFluency())
                        .fluencyScore(fb.getFluencyScore())
                        .content(fb.getContent())
                        .contentScore(fb.getContentScore())
                        .contentQuote(fb.getContentQuote())
                        .contentFix(fb.getContentFix())
                        .overall(fb.getOverall())
                        .overallGrade(fb.getOverallGrade())
                        .improvements(fb.getImprovements())
                        .modelAnswer(fb.getModelAnswer())
                        .modelAnswerComment(fb.getModelAnswerComment())
                        .build())
                .toList();

        List<FeedbackResult> saved = feedbackResultRepository.saveAll(toSave);

        List<FeedbackTag> tagsToSave = new ArrayList<>();
        for (int i = 0; i < saved.size(); i++) {
            List<FeedbackTagDto> tags = validFeedback.get(i).getTags();
            if (tags == null) continue;
            for (var t : tags) {
                tagsToSave.add(FeedbackTag.builder()
                        .feedbackResult(saved.get(i))
                        .category(t.category())
                        .tag(t.tag())
                        .build());
            }
        }
        feedbackTagRepository.saveAll(tagsToSave);

        log.info("[DB 저장] 피드백 {}건, 태그 {}건 (member: {}, combo: {})",
                saved.size(), tagsToSave.size(), member.getId(), attempt.comboCategory());
    }
}
