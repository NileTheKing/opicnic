package com.opicnic.opicnic.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.config.RateLimiterService;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.AttemptStatus;
import com.opicnic.opicnic.dto.ErrorResponse;
import com.opicnic.opicnic.exception.PayloadTooLargeException;
import com.opicnic.opicnic.exception.RateLimitExceededException;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.FinalizeResponseDto;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.dto.SubmissionResponseDto;
import com.opicnic.opicnic.dto.SubmissionResultItemDto;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.attempt.FeedbackPersistenceService;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
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
    private final FeedbackPersistenceService feedbackPersistenceService;
    private final ObjectMapper objectMapper;
    private final RateLimiterService rateLimiterService;

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

    private static final String RESULT_URL = "/practice/feedback/result";

    @PostMapping("/{attemptId}/finalize")
    public ResponseEntity<?> finalize(@PathVariable String attemptId,
                                      HttpSession session,
                                      @AuthenticationPrincipal OAuth2User oAuth2User) {
        // REVIEW-01: submitAnswers/retry와 달리 SUBMITTED를 예외로 던지지 않는 조회를 쓴다 —
        // 이미 완료된 attempt에 대한 재요청(더블클릭, 응답을 못 받은 클라이언트의 네트워크 재시도)을
        // 아래에서 멱등하게(같은 성공 응답) 처리하기 위함이다.
        PracticeAttempt attempt = attemptService.requireAttemptForFinalize(attemptId);
        ResponseEntity<?> forbidden = rejectIfNotOwner(attempt, oAuth2User);
        if (forbidden != null) return forbidden;

        if (attempt.status() == AttemptStatus.SUBMITTED) {
            // 이미 끝난 제출을 다시 finalize해도 410로 막지 않고 같은 성공 응답을 그대로 돌려준다.
            return ResponseEntity.ok(new FinalizeResponseDto(RESULT_URL));
        }

        int questionCount = attempt.questionIds().size();
        Map<Integer, FeedbackDTO> results = getAttemptResults(session, attemptId);
        if (results.size() != questionCount) {
            throw new IllegalArgumentException("아직 모든 문항의 피드백이 완료되지 않았습니다.");
        }

        List<FeedbackDTO> feedbackResults = results.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();

        // REVIEW-01: DB 저장 전에 "이 요청이 제출 처리 권한을 땄는지"를 원자적으로 먼저 확정한다
        // (IN_PROGRESS -> FINALIZING). 동시에 두 번 finalize가 들어와도(더블클릭, 두 탭 등) 정확히
        // 하나만 true를 받으므로 saveFeedbackResults가 두 번 실행되어 피드백이 중복 저장되는 걸 막는다.
        if (!attemptService.tryStartFinalizing(attemptId)) {
            // 이 사이 다른 요청이 이미 SUBMITTED까지 끝냈다면 멱등하게 성공 응답, 아직 처리
            // 중(FINALIZING)이면 지금은 처리할 수 없다는 뜻으로 기존과 같이 예외로 응답한다.
            PracticeAttempt latest = attemptService.requireAttemptForFinalize(attemptId);
            if (latest.status() == AttemptStatus.SUBMITTED) {
                return ResponseEntity.ok(new FinalizeResponseDto(RESULT_URL));
            }
            throw new IllegalStateException("이미 다른 요청이 제출을 처리하고 있습니다.");
        }

        try {
            feedbackPersistenceService.saveFeedbackResults(feedbackResults, oAuth2User, attempt);
        } catch (RuntimeException e) {
            // REVIEW-01: DB 저장 실패 시 SUBMITTED로 확정하지 않고 IN_PROGRESS로 되돌린다.
            // 되돌리지 않으면 FINALIZING에 영구히 멈춰 다음 finalize 시도도 계속 막히고,
            // 사용자는 이미 다 낸 답변을 영영 제출할 수 없게 된다.
            attemptService.revertFinalizing(attemptId);
            throw e;
        }

        attemptService.confirmSubmitted(attemptId);
        removeAttemptResults(session, attemptId);
        session.setAttribute(SESSION_FEEDBACK_RESULTS, feedbackResults);

        return ResponseEntity.ok(new FinalizeResponseDto(RESULT_URL));
    }

    // COST-01: 답변 1건당 STT+채점+태깅 LLM 호출이 나가므로, 실제 외부 호출 전에 입력을 걸러
    // 한 번의 요청으로 비용을 증폭시킬 수 있는 경로(중복/null index, 과도한 파일, 이미 성공한
    // 문항 재제출)를 막는다.
    private static final int MAX_ANSWER_FILE_BYTES = 15 * 1024 * 1024; // 실제 1~2분 webm 음성은 보통 수백 KB~1MB대
    private static final String ALLOWED_ANSWER_CONTENT_TYPE = "audio/webm";

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
        if (questionIndexes.contains(null)) {
            throw new IllegalArgumentException("questionIndexes에 null이 포함될 수 없습니다.");
        }
        if (new java.util.HashSet<>(questionIndexes).size() != questionIndexes.size()) {
            throw new IllegalArgumentException("questionIndexes에 중복된 인덱스가 있습니다.");
        }

        PracticeAttempt attempt = attemptService.requireValidAttempt(attemptId);
        ResponseEntity<?> forbidden = rejectIfNotOwner(attempt, oAuth2User);
        if (forbidden != null) return forbidden;

        if (questionIndexes.size() > attempt.questionIds().size()) {
            throw new IllegalArgumentException("questionIndexes 개수가 문제 수를 초과했습니다.");
        }
        Map<Integer, FeedbackDTO> existingResults = getAttemptResults(request.getSession(), attemptId);
        if (questionIndexes.stream().anyMatch(existingResults::containsKey)) {
            throw new IllegalArgumentException("이미 채점이 완료된 문항은 다시 제출할 수 없습니다.");
        }

        List<QuestionDto> questions = attemptService.restoreQuestionsForIndexes(attemptId, questionIndexes);

        // REVIEW-05: 이 컨트롤러는 Spring의 MultipartResolver를 거치지 않고 request.getParts()로
        // 직접 파싱한다 — 컨테이너 수준 한도(application.yml의 max-request-size 150MB) 초과나
        // multipart가 아닌 Content-Type으로 호출되면 Servlet 스펙상 여기서 IOException/ServletException이
        // 난다. 그대로 흘려보내면 catch-all(500)로 새므로, 크기 초과는 413로, 그 외 파싱 실패는
        // 400으로 구분해서 ApiExceptionHandler가 처리할 수 있는 타입으로 바꿔 던진다.
        List<Part> fileParts = new ArrayList<>();
        try {
            for (var part : request.getParts()) {
                if ("files".equals(part.getName())) fileParts.add(part);
            }
        } catch (IOException | ServletException e) {
            if (isMultipartSizeExceeded(e)) {
                throw new PayloadTooLargeException("첨부파일이 너무 큽니다.");
            }
            throw new IllegalArgumentException("첨부파일 요청을 처리할 수 없습니다. 콘텐츠 타입을 확인해주세요.");
        }

        if (fileParts.size() != questions.size()) {
            throw new IllegalArgumentException("파일 수와 문제 수가 일치하지 않습니다.");
        }
        for (var part : fileParts) {
            if (part.getSize() > MAX_ANSWER_FILE_BYTES) {
                throw new IllegalArgumentException("답변 파일이 너무 큽니다. (최대 " + (MAX_ANSWER_FILE_BYTES / 1024 / 1024) + "MB)");
            }
            String contentType = part.getContentType();
            if (contentType == null || !contentType.startsWith(ALLOWED_ANSWER_CONTENT_TYPE)) {
                throw new IllegalArgumentException("지원하지 않는 파일 형식입니다.");
            }
        }
        List<InputStream> streams = new ArrayList<>();
        for (var part : fileParts) streams.add(part.getInputStream());

        // 검증을 모두 통과한 뒤에야 한도를 소비한다. 인터셉터에서 미리 소비하면 여기까지 오지 못하고
        // 400으로 거부될 요청(중복 index, 대용량 파일 등)도 한도를 깎아먹게 되므로, 실제로 외부
        // 호출이 나가기 직전인 여기서 소비해야 "검증 실패 = 비용 없음 = 한도 소비 없음"이 맞아떨어진다.
        long gradedQuestionCount = questions.stream().filter(q -> q.getId() != null).count();
        int cost = (int) Math.max(1, gradedQuestionCount);
        // FU-03: attempt.memberId()를 함께 넘겨야, dev 프로파일 + memberId=null(DevPracticeController가
        // 만든 k6 부하테스트 attempt)인 경우에만 소비를 건너뛴다. 로그인 회원(dev 포함)과 운영 익명은
        // 기존 한도를 그대로 유지해야 하므로 memberId 없이는 이 구분이 불가능하다.
        if (!rateLimiterService.tryConsume(cost, attempt.memberId())) {
            throw new RateLimitExceededException("시간당 문항 한도를 초과했습니다. 잠시 후 다시 시도해주세요.");
        }

        List<FeedbackDTO> submittedFeedbackResults = feedbackService.getComboFeedbackStreaming(streams, questions);

        List<Integer> failedIndexes = new ArrayList<>();
        for (int i = 0; i < submittedFeedbackResults.size(); i++) {
            FeedbackDTO fb = submittedFeedbackResults.get(i);
            int originalIndex = questionIndexes.get(i);
            if (fb.isFailed()) {
                failedIndexes.add(originalIndex);
            } else {
                existingResults.put(originalIndex, fb);
            }
        }
        saveAttemptResults(request.getSession(), attemptId, existingResults);

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

    // REVIEW-05: 컨테이너(Tomcat)가 요청 크기 한도 초과 시 던지는 예외의 정확한 타입은 구현체마다
    // 다르지만 클래스명/원인 체인 어딘가에 크기 관련 단서(Size)가 남는다. 파싱 실패 원인을
    // "너무 큼"과 "그 외(콘텐츠 타입 등)"로만 구분하면 되므로 문자열 단서로 판별한다.
    private static boolean isMultipartSizeExceeded(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause.getClass().getSimpleName().toLowerCase().contains("size")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
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

}
