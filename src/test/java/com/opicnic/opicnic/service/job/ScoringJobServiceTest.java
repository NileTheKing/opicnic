package com.opicnic.opicnic.service.job;

import com.opicnic.opicnic.config.RateLimiterService;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.AttemptStatus;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.job.ScoringJob;
import com.opicnic.opicnic.domain.job.ScoringJobItemStatus;
import com.opicnic.opicnic.domain.job.ScoringJobStatus;
import com.opicnic.opicnic.dto.job.UploadUrlRequest;
import com.opicnic.opicnic.dto.job.UploadUrlResponse;
import com.opicnic.opicnic.exception.RateLimitExceededException;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.ScoringJobRepository;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import com.opicnic.opicnic.storage.InMemoryAudioStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// 접수 API의 규칙: URL 발급 검증(범위·중복·크기·타입), submit이 DB만 쓰고 끝나는지, 멱등, 한도 소비 순서.
class ScoringJobServiceTest {

    private final PracticeAttemptService attemptService = mock(PracticeAttemptService.class);
    private final ScoringJobRepository jobRepository = mock(ScoringJobRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final RateLimiterService rateLimiter = mock(RateLimiterService.class);
    private final InMemoryAudioStorage storage = new InMemoryAudioStorage();
    private final Member member = Member.builder().id(7L).provider("kakao").providerId("p").build();

    private ScoringJobService service;

    // 자기소개(null) + 채점 문항 2개
    private final PracticeAttempt mockAttempt = new PracticeAttempt("att-1", Arrays.asList(null, 10L, 11L), 7L,
            PracticeMode.MOCK_EXAM, null, null, Instant.now().plusSeconds(3600), AttemptStatus.IN_PROGRESS);

    @BeforeEach
    void setUp() {
        service = new ScoringJobService(attemptService, jobRepository, memberRepository, storage, rateLimiter, Optional.empty());
        when(attemptService.requireValidAttempt("att-1")).thenReturn(mockAttempt);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
        when(jobRepository.findById(any())).thenReturn(Optional.empty());
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(rateLimiter.tryConsume(anyInt(), any())).thenReturn(true);
        when(attemptService.tryStartFinalizing("att-1")).thenReturn(true);
    }

    @Test
    @DisplayName("URL 발급: 문항 수·크기·타입을 통과한 요청만, 키는 서버 규칙(pending/{attemptId}/q{n}.webm)")
    void issueUploadUrls_valid() {
        List<UploadUrlResponse> urls = service.issueUploadUrls("att-1", List.of(
                new UploadUrlRequest(0, 1000, "audio/webm"), new UploadUrlRequest(2, 4 * 1024 * 1024, "audio/webm;codecs=opus")));

        assertThat(urls).hasSize(2);
        assertThat(urls.get(0).url()).endsWith("pending/att-1/q0.webm");
        assertThat(urls.get(1).url()).endsWith("pending/att-1/q2.webm");
        assertThat(urls.get(0).expiresInSeconds()).isEqualTo(600);
    }

    @Test
    @DisplayName("URL 발급 거절: 범위 밖 index, 중복 index, 4MB 초과, audio/webm 아님, 콤보 attempt")
    void issueUploadUrls_rejects() {
        assertThatThrownBy(() -> service.issueUploadUrls("att-1", List.of(new UploadUrlRequest(3, 10, "audio/webm"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("index");
        assertThatThrownBy(() -> service.issueUploadUrls("att-1", List.of(new UploadUrlRequest(1, 10, "audio/webm"), new UploadUrlRequest(1, 10, "audio/webm"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("중복");
        assertThatThrownBy(() -> service.issueUploadUrls("att-1", List.of(new UploadUrlRequest(1, 4 * 1024 * 1024 + 1, "audio/webm"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("너무 큽니다");
        assertThatThrownBy(() -> service.issueUploadUrls("att-1", List.of(new UploadUrlRequest(1, 10, "audio/mp4"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("형식");

        PracticeAttempt combo = new PracticeAttempt("combo-1", List.of(1L, 2L, 3L), 7L,
                PracticeMode.COMBO, "C1", "C1", Instant.now().plusSeconds(3600), AttemptStatus.IN_PROGRESS);
        when(attemptService.requireValidAttempt("combo-1")).thenReturn(combo);
        assertThatThrownBy(() -> service.issueUploadUrls("combo-1", List.of(new UploadUrlRequest(0, 10, "audio/webm"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("모의고사");
    }

    @Test
    @DisplayName("submit: 잡 1 + 문항 N행을 QUEUED로 저장하고 Caffeine attempt를 SUBMITTED로. 한도는 자기소개 제외 문항 수만큼")
    void submit_createsQueuedJob() {
        ScoringJob job = service.submit("att-1", null);

        assertThat(job.getId()).isEqualTo("att-1");
        assertThat(job.getStatus()).isEqualTo(ScoringJobStatus.QUEUED);
        assertThat(job.getItems()).hasSize(3);
        assertThat(job.getItems()).allMatch(i -> i.getStatus() == ScoringJobItemStatus.QUEUED);
        assertThat(job.getItems().get(0).getQuestionId()).isNull();
        assertThat(job.getItems().get(1).getAudioKey()).isEqualTo("pending/att-1/q1.webm");
        assertThat(job.getMember()).isSameAs(member);

        verify(rateLimiter).tryConsume(eq(2), eq(7L));   // 자기소개 제외 2문항
        verify(attemptService).tryStartFinalizing("att-1");
        verify(attemptService).confirmSubmitted("att-1");
        ArgumentCaptor<ScoringJob> saved = ArgumentCaptor.forClass(ScoringJob.class);
        verify(jobRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(job);
    }

    @Test
    @DisplayName("submit 멱등: 잡이 이미 있으면 그대로 돌려주고 한도·상태 전이를 다시 하지 않는다")
    void submit_idempotent() {
        ScoringJob existing = new ScoringJob("att-1", member, PracticeMode.MOCK_EXAM);
        when(jobRepository.findById("att-1")).thenReturn(Optional.of(existing));

        ScoringJob job = service.submit("att-1", null);

        assertThat(job).isSameAs(existing);
        verify(rateLimiter, never()).tryConsume(anyInt(), any());
        verify(jobRepository, never()).save(any());
    }

    @Test
    @DisplayName("submit: 한도 초과면 429 예외, 잡을 만들지 않는다")
    void submit_rateLimited() {
        when(rateLimiter.tryConsume(anyInt(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.submit("att-1", null)).isInstanceOf(RateLimitExceededException.class);
        verify(jobRepository, never()).save(any());
        verify(attemptService, never()).tryStartFinalizing(any());
    }

    @Test
    @DisplayName("submit: 로그인 없는 attempt(memberId=null)는 dev 테스터 회원이 없으면 거절")
    void submit_anonymousWithoutDevTester() {
        PracticeAttempt anon = new PracticeAttempt("anon-1", Arrays.asList(null, 10L), null,
                PracticeMode.MOCK_EXAM, null, null, Instant.now().plusSeconds(3600), AttemptStatus.IN_PROGRESS);
        when(attemptService.requireValidAttempt("anon-1")).thenReturn(anon);

        assertThatThrownBy(() -> service.submit("anon-1", null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("로그인");
    }
}
