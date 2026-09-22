package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.job.ScoringJob;
import com.opicnic.opicnic.dto.ErrorResponse;
import com.opicnic.opicnic.dto.job.ScoringJobCreateRequest;
import com.opicnic.opicnic.dto.job.ScoringJobStatusResponse;
import com.opicnic.opicnic.dto.job.UploadUrlRequest;
import com.opicnic.opicnic.dto.job.UploadUrlResponse;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import com.opicnic.opicnic.service.job.ScoringJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;

// 비동기 채점 REST API (ADR-0001). 제출 전 리소스는 practice-attempts, 제출 후는 scoring-jobs.
//   POST /api/practice-attempts/{attemptId}/upload-urls   녹음 끝난 문항의 presigned PUT URL
//   POST /api/scoring-jobs {attemptId}                     접수 = 잡 생성 → 202 + Location
//   GET  /api/scoring-jobs/{id}                            폴링
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ScoringJobApiController {

    private final ScoringJobService scoringJobService;
    private final PracticeAttemptService attemptService;
    private final MemberRepository memberRepository;

    @PostMapping("/practice-attempts/{attemptId}/upload-urls")
    public ResponseEntity<?> issueUploadUrls(@PathVariable String attemptId,
                                             @RequestBody List<UploadUrlRequest> requests,
                                             @AuthenticationPrincipal OAuth2User oAuth2User) {
        PracticeAttempt attempt = attemptService.requireValidAttempt(attemptId);
        ResponseEntity<?> forbidden = rejectIfNotOwner(attempt.memberId(), oAuth2User);
        if (forbidden != null) return forbidden;
        List<UploadUrlResponse> urls = scoringJobService.issueUploadUrls(attemptId, requests);
        return ResponseEntity.ok(urls);
    }

    @PostMapping("/scoring-jobs")
    public ResponseEntity<?> create(@RequestBody ScoringJobCreateRequest request,
                                    @AuthenticationPrincipal OAuth2User oAuth2User) {
        if (request == null || request.attemptId() == null || request.attemptId().isBlank()) {
            throw new IllegalArgumentException("attemptId는 필수입니다.");
        }
        // 멱등 재요청은 attempt가 이미 SUBMITTED라 requireValidAttempt가 410을 던지므로 잡을 먼저 본다
        Optional<ScoringJob> existing = scoringJobService.find(request.attemptId());
        if (existing.isEmpty()) {
            PracticeAttempt attempt = attemptService.requireValidAttempt(request.attemptId());
            ResponseEntity<?> forbidden = rejectIfNotOwner(attempt.memberId(), oAuth2User);
            if (forbidden != null) return forbidden;
        } else {
            ResponseEntity<?> forbidden = rejectIfNotOwner(existing.get().getMember().getId(), oAuth2User);
            if (forbidden != null) return forbidden;
        }
        ScoringJob job = scoringJobService.submit(request.attemptId(), findMember(oAuth2User));
        return ResponseEntity.accepted()
                .location(URI.create("/api/scoring-jobs/" + job.getId()))
                .body(ScoringJobStatusResponse.from(job));
    }

    @GetMapping("/scoring-jobs/{id}")
    public ResponseEntity<?> status(@PathVariable String id, @AuthenticationPrincipal OAuth2User oAuth2User) {
        ScoringJob job = scoringJobService.find(id)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 채점 작업입니다."));
        ResponseEntity<?> forbidden = rejectIfNotOwner(job.getMember().getId(), oAuth2User);
        if (forbidden != null) return forbidden;
        return ResponseEntity.ok(ScoringJobStatusResponse.from(job));
    }

    // memberId==null(dev attempt)은 누구나.
    // dev 테스터 회원이 주인인 잡도 마찬가지로 열어둔다 — 측정 스크립트가 로그인 없이 폴링해야 한다.
    private ResponseEntity<?> rejectIfNotOwner(Long ownerId, OAuth2User oAuth2User) {
        if (ownerId == null) return null;
        Member owner = memberRepository.findById(ownerId).orElse(null);
        if (owner != null && "dev".equals(owner.getProvider())) return null;
        if (oAuth2User == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("로그인이 필요합니다."));
        }
        Member requester = findMember(oAuth2User);
        if (requester == null || !ownerId.equals(requester.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("해당 연습 세션에 접근할 수 없습니다."));
        }
        return null;
    }

    private Member findMember(OAuth2User oAuth2User) {
        if (oAuth2User == null) return null;
        String provider = oAuth2User.getAttribute("provider");
        String providerId = oAuth2User.getAttribute("providerId");
        return memberRepository.findByProviderAndProviderId(provider, providerId).orElse(null);
    }
}
