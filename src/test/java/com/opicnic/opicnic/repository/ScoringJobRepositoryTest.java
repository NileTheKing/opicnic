package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.enums.Role;
import com.opicnic.opicnic.domain.job.ScoringJob;
import com.opicnic.opicnic.domain.job.ScoringJobItem;
import com.opicnic.opicnic.domain.job.ScoringJobItem.FailureKind;
import com.opicnic.opicnic.domain.job.ScoringJobItem.FailureReason;
import com.opicnic.opicnic.domain.job.ScoringJobItemStatus;
import com.opicnic.opicnic.domain.job.ScoringJobStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// ADR-0001 잡 테이블의 핵심 전이가 실제 DB에서 성립하는지. 워커가 이 규칙에 기댄다:
// (1) claim은 정확히 한 번만 성공 (2) 실패는 3회까지 QUEUED로 돌아가고 3회째 FAILED
// (3) 전 문항이 끝나야 attempt가 닫히고, FAILED가 있으면 COMPLETED_WITH_FAILURES
// (4) PROCESSING에 오래 멈춘 문항은 requeueStale로 회수
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ScoringJobRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb").withUsername("testuser").withPassword("testpass");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired ScoringJobRepository jobRepository;
    @Autowired ScoringJobItemRepository itemRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;

    private ScoringJob queuedJob(int items) {
        Member member = memberRepository.save(Member.builder()
                .provider("kakao").providerId("p-" + UUID.randomUUID()).nickname("n").role(Role.USER).build());
        ScoringJob job = new ScoringJob(UUID.randomUUID().toString(), member, PracticeMode.MOCK_EXAM);
        for (int i = 0; i < items; i++) job.addItem(i, i == 0 ? null : (long) i, "pending/" + job.getId() + "/q" + i + ".webm");
        jobRepository.saveAndFlush(job);
        em.clear();
        return job;
    }

    @Test
    void claimSucceedsExactlyOnce() {
        ScoringJob job = queuedJob(1);
        Long itemId = jobRepository.findById(job.getId()).orElseThrow().getItems().get(0).getId();

        assertThat(itemRepository.claim(itemId)).isEqualTo(1);
        assertThat(itemRepository.claim(itemId)).isEqualTo(0);

        ScoringJobItem item = itemRepository.findById(itemId).orElseThrow();
        assertThat(item.getStatus()).isEqualTo(ScoringJobItemStatus.PROCESSING);
        assertThat(item.getAttempts()).isEqualTo(1);
    }

    @Test
    void transientFailureRequeuesWhileBudgetLastsThenFails() {
        ScoringJob job = queuedJob(1);
        Long itemId = jobRepository.findById(job.getId()).orElseThrow().getItems().get(0).getId();
        LocalDateTime deadline = LocalDateTime.now().plusMinutes(30);

        // 예산 안: 몇 번을 실패해도 다시 큐로 (옛 3회 상한 없음)
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(itemRepository.claim(itemId)).isEqualTo(1);
            ScoringJobItem item = itemRepository.findById(itemId).orElseThrow();
            item.markFailed("429", FailureKind.TRANSIENT, Duration.ZERO, deadline);
            itemRepository.saveAndFlush(item);
            em.clear();
            assertThat(itemRepository.findById(itemId).orElseThrow().getStatus()).isEqualTo(ScoringJobItemStatus.QUEUED);
        }

        // 다음 시도가 예산을 넘으면 FAILED, 사유는 예산 초과
        assertThat(itemRepository.claim(itemId)).isEqualTo(1);
        ScoringJobItem item = itemRepository.findById(itemId).orElseThrow();
        item.markFailed("503", FailureKind.TRANSIENT, Duration.ofMinutes(31), deadline);
        itemRepository.saveAndFlush(item);
        em.clear();
        ScoringJobItem failed = itemRepository.findById(itemId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(ScoringJobItemStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo(FailureReason.RETRY_BUDGET_EXCEEDED);
        // FAILED는 더 집히지 않는다
        assertThat(itemRepository.claim(itemId)).isEqualTo(0);
    }

    @Test
    void permanentFailureFailsImmediately() {
        ScoringJob job = queuedJob(1);
        Long itemId = jobRepository.findById(job.getId()).orElseThrow().getItems().get(0).getId();

        assertThat(itemRepository.claim(itemId)).isEqualTo(1);
        ScoringJobItem item = itemRepository.findById(itemId).orElseThrow();
        item.markFailed("NoSuchKey", FailureKind.PERMANENT, Duration.ZERO, LocalDateTime.now().plusMinutes(30));
        itemRepository.saveAndFlush(item);
        em.clear();

        ScoringJobItem failed = itemRepository.findById(itemId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(ScoringJobItemStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo(FailureReason.AUDIO);
    }

    @Test
    void invalidOutputFailsOnThirdEvenWithinBudget() {
        ScoringJob job = queuedJob(1);
        Long itemId = jobRepository.findById(job.getId()).orElseThrow().getItems().get(0).getId();
        LocalDateTime deadline = LocalDateTime.now().plusMinutes(30);

        // 일시적 실패가 섞여도 형식 오류 횟수만 센다
        assertThat(itemRepository.claim(itemId)).isEqualTo(1);
        ScoringJobItem first = itemRepository.findById(itemId).orElseThrow();
        first.markFailed("timeout", FailureKind.TRANSIENT, Duration.ZERO, deadline);
        itemRepository.saveAndFlush(first);
        em.clear();

        for (int n = 1; n <= ScoringJobItem.MAX_INVALID_OUTPUTS; n++) {
            assertThat(itemRepository.claim(itemId)).isEqualTo(1);
            ScoringJobItem item = itemRepository.findById(itemId).orElseThrow();
            item.markFailed("contentScore 누락", FailureKind.INVALID_OUTPUT, Duration.ZERO, deadline);
            itemRepository.saveAndFlush(item);
            em.clear();
            ScoringJobItemStatus expected = n < ScoringJobItem.MAX_INVALID_OUTPUTS
                    ? ScoringJobItemStatus.QUEUED : ScoringJobItemStatus.FAILED;
            assertThat(itemRepository.findById(itemId).orElseThrow().getStatus()).isEqualTo(expected);
        }
        assertThat(itemRepository.findById(itemId).orElseThrow().getFailureReason()).isEqualTo(FailureReason.INVALID_OUTPUT);
    }

    @Test
    void jobClosesOnlyWhenEveryItemFinished_withFailuresFlag() {
        ScoringJob job = queuedJob(2);
        ScoringJob loaded = jobRepository.findById(job.getId()).orElseThrow();
        Long secondId = loaded.getItems().get(1).getId();

        loaded.getItems().get(0).markDone(null);
        loaded.refreshCompletion();
        assertThat(loaded.getStatus()).isEqualTo(ScoringJobStatus.QUEUED); // 아직 하나 남음
        jobRepository.saveAndFlush(loaded);
        em.clear();

        // 두 번째 문항은 녹음 파일 문제 → 즉시 FAILED
        assertThat(itemRepository.claim(secondId)).isEqualTo(1);
        ScoringJobItem second = itemRepository.findById(secondId).orElseThrow();
        second.markFailed("NoSuchKey", FailureKind.PERMANENT, Duration.ZERO, LocalDateTime.now().plusMinutes(30));
        itemRepository.saveAndFlush(second);
        em.clear();

        loaded = jobRepository.findById(job.getId()).orElseThrow();
        loaded.refreshCompletion();
        jobRepository.saveAndFlush(loaded);

        assertThat(loaded.getItems().get(1).getStatus()).isEqualTo(ScoringJobItemStatus.FAILED);
        assertThat(loaded.getStatus()).isEqualTo(ScoringJobStatus.COMPLETED_WITH_FAILURES);
        assertThat(loaded.getCompletedAt()).isNotNull();
    }

    @Test
    void staleProcessingItemsAreRequeued() {
        ScoringJob job = queuedJob(1);
        Long itemId = jobRepository.findById(job.getId()).orElseThrow().getItems().get(0).getId();
        itemRepository.claim(itemId);

        assertThat(itemRepository.requeueStale(LocalDateTime.now().minusMinutes(5))).isEqualTo(0); // 방금 집은 건 회수 안 함
        assertThat(itemRepository.requeueStale(LocalDateTime.now().plusMinutes(1))).isEqualTo(1);  // 기준 시각을 미래로 두면 회수
        assertThat(itemRepository.findById(itemId).orElseThrow().getStatus()).isEqualTo(ScoringJobItemStatus.QUEUED);
    }
}
