package com.opicnic.opicnic.service.job;

import com.opicnic.opicnic.config.RateLimiterService;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.job.ScoringJob;
import com.opicnic.opicnic.dto.job.UploadUrlRequest;
import com.opicnic.opicnic.dto.job.UploadUrlResponse;
import com.opicnic.opicnic.exception.RateLimitExceededException;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.ScoringJobRepository;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import com.opicnic.opicnic.storage.AudioStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// 비동기 채점 접수 (ADR-0001 4절 ①~③). 제출 전은 Caffeine PracticeAttempt, 제출 후는 DB ScoringJob —
// 리소스 경계(/api/practice-attempts vs /api/scoring-jobs)가 저장소 경계와 같다.
// 모의고사·콤보·유형별 연습 모두 이 경로 하나다(2026-09-21 동기 경로 제거).
@Service
@RequiredArgsConstructor
@Slf4j
public class ScoringJobService {

    // 톰캣 max-file-size(4MB)와 같은 값. 직접 업로드는 서버 방어선을 하나도 안 거치므로 서명에 박아 R2가 막게 한다.
    static final long MAX_AUDIO_BYTES = 4L * 1024 * 1024;
    static final String AUDIO_CONTENT_TYPE = "audio/webm";
    static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(10);

    private final PracticeAttemptService attemptService;
    private final ScoringJobRepository jobRepository;
    private final MemberRepository memberRepository;
    private final AudioStorage audioStorage;
    private final RateLimiterService rateLimiterService;
    private final Optional<DevTesterMember> devTesterMember;   // dev 프로파일에만 존재

    // 키는 서버가 정한다 — 클라이언트가 고르게 두지 않는다. submit 시 같은 규칙으로 다시 만들므로 저장할 필요 없음.
    // pending/ 프리픽스는 R2 라이프사이클 1일 (scripts/r2/lifecycle.json). 워커가 처리 후 attempts/로 옮긴다(30일).
    static String pendingKey(String attemptId, int index) {
        return "pending/" + attemptId + "/q" + index + ".webm";
    }

    public List<UploadUrlResponse> issueUploadUrls(String attemptId, List<UploadUrlRequest> requests) {
        PracticeAttempt attempt = requireAttempt(attemptId);
        int questionCount = attempt.questionIds().size();
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("업로드할 문항이 없습니다.");
        }
        Set<Integer> seen = new HashSet<>();
        for (UploadUrlRequest r : requests) {
            if (r.index() < 0 || r.index() >= questionCount) {
                throw new IllegalArgumentException("문제 index가 유효하지 않습니다.");
            }
            if (!seen.add(r.index())) {
                throw new IllegalArgumentException("중복된 문제 index가 있습니다.");
            }
            if (r.size() <= 0 || r.size() > MAX_AUDIO_BYTES) {
                throw new IllegalArgumentException("답변 파일이 너무 큽니다. (최대 " + (MAX_AUDIO_BYTES / 1024 / 1024) + "MB)");
            }
            if (r.contentType() == null || !r.contentType().startsWith(AUDIO_CONTENT_TYPE)) {
                throw new IllegalArgumentException("지원하지 않는 파일 형식입니다.");
            }
        }
        return requests.stream()
                .map(r -> new UploadUrlResponse(r.index(),
                        audioStorage.presignPut(pendingKey(attemptId, r.index()), r.size(), r.contentType(), UPLOAD_URL_TTL).toString(),
                        UPLOAD_URL_TTL.toSeconds()))
                .toList();
    }

    // 접수. DB만 쓰고 끝난다 — R2 확인(HEAD)도, 외부 호출도 없다. 안 올라간 파일은 워커가 읽을 때 발견해 FAILED.
    // 멱등: 같은 attemptId로 두 번 오면(더블클릭, 응답 유실 후 재시도) 이미 만든 잡을 그대로 돌려준다.
    @Transactional
    public ScoringJob submit(String attemptId, Member requester) {
        Optional<ScoringJob> existing = jobRepository.findById(attemptId);
        if (existing.isPresent()) {
            return existing.get();
        }
        PracticeAttempt attempt = requireAttempt(attemptId);
        Member owner = resolveOwner(attempt, requester);

        // 채점 문항(자기소개 제외) 수만큼 한도 소비. 검증을 다 통과한 뒤, DB 저장 직전 — 기존 동기 경로와 같은 순서.
        long gradedCount = attempt.questionIds().stream().filter(id -> id != null).count();
        if (!rateLimiterService.tryConsume((int) Math.max(1, gradedCount), attempt.memberId())) {
            throw new RateLimitExceededException("시간당 문항 한도를 초과했습니다. 잠시 후 다시 시도해주세요.");
        }

        // Caffeine attempt를 SUBMITTED로 — 같은 attempt로 URL 발급·재접수가 끼어들지 못하게.
        // 동시에 두 submit이 오면 하나만 이 전이에 성공한다(REVIEW-01과 같은 장치).
        if (!attemptService.tryStartFinalizing(attemptId)) {
            return jobRepository.findById(attemptId)
                    .orElseThrow(() -> new IllegalStateException("이미 다른 요청이 제출을 처리하고 있습니다."));
        }
        ScoringJob job = new ScoringJob(attemptId, owner, attempt.mode(), attempt.comboPatternKey(), attempt.comboCategory());
        List<Long> questionIds = attempt.questionIds();
        for (int i = 0; i < questionIds.size(); i++) {
            job.addItem(i, questionIds.get(i), pendingKey(attemptId, i));
        }
        jobRepository.save(job);
        attemptService.confirmSubmitted(attemptId);
        log.info("[ScoringJob] 접수 attempt={} items={}", attemptId, questionIds.size());
        return job;
    }

    @Transactional(readOnly = true)
    public Optional<ScoringJob> find(String jobId) {
        return jobRepository.findById(jobId);
    }

    // 사용자가 끝난 결과를 봤다 — 홈의 "끝난 결과가 있어요" 배너를 내린다. 끝나지 않았으면 아무것도 안 한다
    @Transactional
    public void markResultSeen(String jobId) {
        jobRepository.findById(jobId).ifPresent(ScoringJob::markResultSeen);
    }

    // 홈 배너용: 최근 하루 안에 접수했고 결과를 아직 안 본 가장 최근 잡(채점 중이거나 끝났는데 안 봤거나)
    @Transactional(readOnly = true)
    public Optional<ScoringJob> findUnseen(Long memberId) {
        return jobRepository.findFirstByMemberIdAndResultSeenAtIsNullAndCreatedAtAfterOrderByCreatedAtDesc(
                memberId, LocalDateTime.now().minusDays(1));
    }

    private PracticeAttempt requireAttempt(String attemptId) {
        return attemptService.requireValidAttempt(attemptId);
    }

    // attempt에 memberId가 있으면 그 회원이 주인. 없으면(dev의 로그인 없는 attempt) dev 테스터 회원.
    private Member resolveOwner(PracticeAttempt attempt, Member requester) {
        if (attempt.memberId() != null) {
            return memberRepository.findById(attempt.memberId())
                    .orElseThrow(() -> new IllegalStateException("attempt의 회원을 찾을 수 없습니다."));
        }
        if (requester != null) return requester;
        return devTesterMember.map(DevTesterMember::get)
                .orElseThrow(() -> new IllegalStateException("로그인이 필요합니다."));
    }
}
