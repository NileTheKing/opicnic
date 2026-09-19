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

    public static final int MAX_ATTEMPTS = 3;

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

    // 워커가 이 문항을 집은 횟수(ScoringJobItemRepository.claim이 올림). MAX_ATTEMPTS 도달 시 FAILED 확정 —
    // 버그 하나로 할당량을 다 태우지 않게
    @Column(nullable = false)
    private int attempts;

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

    // 실패 시: 시도가 남았으면 QUEUED로 되돌려 backoff 뒤에 다시 집히게, 소진했으면 FAILED 확정
    public void markFailed(String error, java.time.Duration backoff) {
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        if (attempts >= MAX_ATTEMPTS) {
            this.status = ScoringJobItemStatus.FAILED;
            this.nextAttemptAt = null;
        } else {
            this.status = ScoringJobItemStatus.QUEUED;
            this.nextAttemptAt = LocalDateTime.now().plus(backoff);
        }
    }

    public void markFailed(String error) {
        markFailed(error, java.time.Duration.ZERO);
    }

    public boolean isFinished() {
        return status == ScoringJobItemStatus.DONE || status == ScoringJobItemStatus.FAILED;
    }
}
