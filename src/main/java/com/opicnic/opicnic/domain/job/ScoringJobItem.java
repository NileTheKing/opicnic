package com.opicnic.opicnic.domain.job;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "scoring_job_item",
        uniqueConstraints = @UniqueConstraint(name = "uk_scoring_job_item_job_index", columnNames = {"job_id", "question_index"}),
        indexes = @Index(name = "idx_scoring_job_item_status", columnList = "status, updated_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoringJobItem {

    // LLM 형식 오류(점수 누락·범위 밖)만 횟수로 끊는다 — 같은 답변에 계속 이상한 형식이 오면 기다려도 안 풀린다.
    // 일시적 실패(429·5xx·타임아웃)는 횟수가 아니라 시간 예산(ScoringWorker retryBudget)으로 끊는다.
    public static final int MAX_INVALID_OUTPUTS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private ScoringJob job;

    @Column(name = "question_index", nullable = false)
    private int questionIndex;

    // null = 자기소개 (DB Question이 아닌 고정 문항). PracticeAttempt.questionIds와 같은 규약
    private Long questionId;

    // R2 객체 키. 서버가 정한다 — 클라이언트가 키를 고르게 두지 않는다 (ADR-0001 4절)
    @Column(nullable = false, length = 200)
    private String audioKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScoringJobItemStatus status;

    // 워커가 이 문항을 집은 횟수(ScoringJobItemRepository.claim이 올림). 백오프 간격을 정하는 데 쓴다
    @Column(nullable = false)
    private int attempts;

    // LLM 형식 오류 횟수. MAX_INVALID_OUTPUTS 도달 시 FAILED
    @Column(nullable = false, columnDefinition = "int default 0")
    private int invalidOutputs;

    // FAILED일 때 왜 끝났는지 — 사용자에게 보여줄 문구가 갈린다. null이면 옛 3회 상한 시절의 FAILED
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FailureReason failureReason;

    @Column(length = 500)
    private String lastError;

    // STT 성공분. 채점만 실패해 다시 집혔을 때 STT를 다시 부르지 않는다 — 동기 경로의 speechText 재사용을
    // DB에 옮긴 것이라 재시작 후에도 유지된다 (2026-08-31 STT 자가 429의 워커 버전 방지)
    @Column(columnDefinition = "TEXT")
    private String sttText;

    // 이 시각 전에는 워커가 집지 않는다 — 재시도 백오프. 동기 경로의 Thread.sleep을 DB로 옮긴 것.
    // null이면 즉시 가능
    private LocalDateTime nextAttemptAt;

    // DONE이면 저장된 FeedbackResult. 자기소개는 채점 안 하므로 DONE이어도 null
    private Long feedbackResultId;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    ScoringJobItem(ScoringJob job, int questionIndex, Long questionId, String audioKey) {
        this.job = job;
        this.questionIndex = questionIndex;
        this.questionId = questionId;
        this.audioKey = audioKey;
        this.status = ScoringJobItemStatus.QUEUED;
    }

    public void markDone(Long feedbackResultId) {
        this.status = ScoringJobItemStatus.DONE;
        this.feedbackResultId = feedbackResultId;
        this.lastError = null;
    }

    public void rememberSpeech(String sttText) {
        this.sttText = sttText;
    }

    // 실패 시 분류대로: 영구 실패는 즉시 FAILED, 형식 오류는 3회째 FAILED, 일시적 실패는 다음 시도가
    // 예산(deadline)을 넘으면 FAILED. 아니면 QUEUED로 되돌려 backoff 뒤에 다시 집히게 한다.
    public void markFailed(String error, FailureKind kind, java.time.Duration backoff, LocalDateTime deadline) {
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        LocalDateTime next = LocalDateTime.now().plus(backoff);
        FailureReason reason = switch (kind) {
            case PERMANENT -> FailureReason.AUDIO;
            case INVALID_OUTPUT -> ++invalidOutputs >= MAX_INVALID_OUTPUTS ? FailureReason.INVALID_OUTPUT : null;
            case TRANSIENT -> next.isAfter(deadline) ? FailureReason.RETRY_BUDGET_EXCEEDED : null;
        };
        if (reason != null) {
            this.status = ScoringJobItemStatus.FAILED;
            this.failureReason = reason;
            this.nextAttemptAt = null;
        } else {
            this.status = ScoringJobItemStatus.QUEUED;
            this.nextAttemptAt = next;
        }
    }

    public enum FailureKind { TRANSIENT, PERMANENT, INVALID_OUTPUT }

    public enum FailureReason { AUDIO, INVALID_OUTPUT, RETRY_BUDGET_EXCEEDED }

    public boolean isFinished() {
        return status == ScoringJobItemStatus.DONE || status == ScoringJobItemStatus.FAILED;
    }
}
