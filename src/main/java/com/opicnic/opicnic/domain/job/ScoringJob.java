package com.opicnic.opicnic.domain.job;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// ADR-0001의 "잡 테이블". 비동기 채점 접수 하나 = 행 하나. submit 시점에 QUEUED로 생성된다 —
// 202 약속을 서버 재시작 후에도 지키려면 접수 사실이 프로세스 밖에 있어야 한다. submit 전(문제 조립·
// 녹음·업로드)은 약속 전이므로 Caffeine PracticeAttempt가 들고 있고, 여기 남지 않는다.
// 1단계에서는 모의고사(MOCK_EXAM)만 이 경로를 탄다. 콤보는 동기 경로 유지.
@Entity
@Table(name = "scoring_job", indexes = {
        @Index(name = "idx_scoring_job_member_created", columnList = "member_id, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoringJob {

    // attemptId — 클라이언트에 노출되는 식별자. 추측 불가해야 하므로 UUID
    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PracticeMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ScoringJobStatus status;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("questionIndex ASC")
    private List<ScoringJobItem> items = new ArrayList<>();

    // = submit 시각. 행이 submit에서 생기므로 별도 submittedAt이 없다
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    // id는 Caffeine PracticeAttempt의 attemptId를 그대로 쓴다 — 클라이언트가 submit 전후로 같은 id로 대화한다
    public ScoringJob(String id, Member member, PracticeMode mode) {
        this.id = id;
        this.member = member;
        this.mode = mode;
        this.status = ScoringJobStatus.QUEUED;
    }

    public ScoringJobItem addItem(int questionIndex, Long questionId, String audioKey) {
        ScoringJobItem item = new ScoringJobItem(this, questionIndex, questionId, audioKey);
        items.add(item);
        return item;
    }

    public void markProcessing() {
        if (status == ScoringJobStatus.QUEUED) this.status = ScoringJobStatus.PROCESSING;
    }

    // 워커가 문항 하나를 끝낼 때마다 부른다. 전 문항이 끝났으면 attempt를 닫는다.
    public void refreshCompletion() {
        boolean allFinished = items.stream().allMatch(ScoringJobItem::isFinished);
        if (!allFinished) return;
        boolean anyFailed = items.stream().anyMatch(i -> i.getStatus() == ScoringJobItemStatus.FAILED);
        this.status = anyFailed ? ScoringJobStatus.COMPLETED_WITH_FAILURES : ScoringJobStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public boolean isFinished() {
        return status == ScoringJobStatus.COMPLETED || status == ScoringJobStatus.COMPLETED_WITH_FAILURES;
    }
}
