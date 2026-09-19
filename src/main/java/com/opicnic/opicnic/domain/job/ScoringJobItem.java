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

    // 실패 시: 시도가 남았으면 QUEUED로 되돌려 다음 폴링에 다시 집히게, 소진했으면 FAILED 확정
    public void markFailed(String error) {
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        this.status = attempts >= MAX_ATTEMPTS ? ScoringJobItemStatus.FAILED : ScoringJobItemStatus.QUEUED;
    }

    public boolean isFinished() {
        return status == ScoringJobItemStatus.DONE || status == ScoringJobItemStatus.FAILED;
    }
}
