package com.opicnic.opicnic.service.job;

import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.job.ScoringJob;
import com.opicnic.opicnic.domain.job.ScoringJobItem;
import com.opicnic.opicnic.domain.job.ScoringJobItemStatus;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.ScoringJobItemRepository;
import com.opicnic.opicnic.repository.ScoringJobRepository;
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.attempt.FeedbackPersistenceService;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import com.opicnic.opicnic.storage.AudioStorage;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

// ADR-0001 4절 ④. DB(잡 테이블)를 큐로 쓰는 워커. 서버·컨트롤러와 직접 대화하지 않고 DB로만 만난다 —
// 그래서 재시작하면 QUEUED/PROCESSING 행을 다시 주워 이어간다.
//
// 한 문항 처리: claim → R2 읽기 → STT(성공분은 DB에 남김) → 채점·태깅 → [FeedbackResult 저장 + DONE]을 한 트랜잭션.
// 실패: markFailed → 백오프 뒤 QUEUED(다시 집힘) 또는 3회째 FAILED. 재시도는 "다시 집기"다. 내부 루프 없음.
//
// 동시 상한(opicnic.worker.concurrency)은 우리 서버가 아니라 제공자 한도 ÷ 문항당 사용량으로 정하는 값이다 —
// 가상 스레드라 스레드는 무한이지만 외부에 300건을 한꺼번에 쏘면 429 폭탄이다. 무료 티어면 1, 종량제면 수백.
@Component
@Slf4j
public class ScoringWorker {

    static final Duration STALE_AFTER = Duration.ofMinutes(5);

    private final ScoringJobRepository jobRepository;
    private final ScoringJobItemRepository itemRepository;
    private final PracticeAttemptService attemptService;
    private final FeedbackService feedbackService;
    private final FeedbackPersistenceService persistence;
    private final AudioStorage audioStorage;
    private final TransactionTemplate tx;
    private final MeterRegistry meterRegistry;
    private final boolean enabled;
    private final Semaphore slots;
    private final FailureCircuit circuit;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger inFlight = new AtomicInteger();

    public ScoringWorker(ScoringJobRepository jobRepository, ScoringJobItemRepository itemRepository,
                         PracticeAttemptService attemptService, FeedbackService feedbackService,
                         FeedbackPersistenceService persistence, AudioStorage audioStorage,
                         TransactionTemplate tx, MeterRegistry meterRegistry,
                         @Value("${opicnic.worker.enabled:true}") boolean enabled,
                         @Value("${opicnic.worker.concurrency:30}") int concurrency,
                         @Value("${opicnic.worker.circuit.window:20}") int circuitWindow,
                         @Value("${opicnic.worker.circuit.failure-ratio:0.8}") double circuitFailureRatio,
                         @Value("${opicnic.worker.circuit.open-seconds:30}") long circuitOpenSeconds) {
        this.jobRepository = jobRepository;
        this.itemRepository = itemRepository;
        this.attemptService = attemptService;
        this.feedbackService = feedbackService;
        this.persistence = persistence;
        this.audioStorage = audioStorage;
        this.tx = tx;
        this.meterRegistry = meterRegistry;
        this.enabled = enabled;
        this.slots = new Semaphore(concurrency);
        this.circuit = new FailureCircuit(circuitWindow, circuitFailureRatio, Duration.ofSeconds(circuitOpenSeconds));
        meterRegistry.gauge("opicnic.worker.in_flight", inFlight);
        meterRegistry.gauge("opicnic.worker.circuit_open", circuit, c -> c.isOpen() ? 1 : 0);
        log.info("[Worker] enabled={} concurrency={} circuit(window={}, ratio={}, open={}s)",
                enabled, concurrency, circuitWindow, circuitFailureRatio, circuitOpenSeconds);
    }

    // 기동 직후: PROCESSING인 문항은 전부 죽은 프로세스의 것이다(단일 인스턴스 전제, ADR-0001 6절) — 5분 기다리지 않고
    // 바로 회수해야 "재시작 포함 완료 < 1분"이 선다. 다중 인스턴스로 가면 이 즉시 회수는 빼고 STALE_AFTER 스윕만 남긴다.
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOrphans() {
        if (!enabled) return;
        int requeued = itemRepository.requeueStale(LocalDateTime.now().plusSeconds(1));
        if (requeued > 0) log.warn("[Worker] 기동 시 PROCESSING 문항 {}건 회수 (이전 프로세스가 처리 중 종료)", requeued);
    }

    // 1초마다: 죽은 워커의 문항 회수 → 빈 슬롯만큼 후보 조회 → claim 성공한 것만 처리 시작
    @Scheduled(fixedDelayString = "${opicnic.worker.poll-ms:1000}")
    public void poll() {
        if (!enabled) return;
        int requeued = itemRepository.requeueStale(LocalDateTime.now().minus(STALE_AFTER));
        if (requeued > 0) log.warn("[Worker] PROCESSING에 {}분 이상 멈춘 문항 {}건 회수", STALE_AFTER.toMinutes(), requeued);

        if (circuit.isOpen()) return;
        int free = slots.availablePermits();
        if (free == 0) return;

        List<ScoringJobItem> candidates = itemRepository.findClaimable(free);
        for (ScoringJobItem candidate : candidates) {
            if (!slots.tryAcquire()) break;
            if (itemRepository.claim(candidate.getId()) != 1) {   // 다른 폴링/인스턴스가 먼저 집음
                slots.release();
                continue;
            }
            inFlight.incrementAndGet();
            Long itemId = candidate.getId();
            executor.submit(() -> {
                try {
                    process(itemId);
                } finally {
                    inFlight.decrementAndGet();
                    slots.release();
                }
            });
        }
    }

    // 트랜잭션은 DB를 만지는 짧은 구간에만. Groq를 기다리는 수 초 동안 커넥션을 물고 있지 않는다.
    void process(Long itemId) {
        ItemContext ctx = tx.execute(s -> {
            ScoringJobItem item = itemRepository.findById(itemId).orElseThrow();
            ScoringJob job = item.getJob();
            return new ItemContext(item.getId(), job.getId(), job.getMember().getId(), item.getQuestionIndex(),
                    item.getQuestionId(), item.getAudioKey(), item.getSttText(), item.getAttempts());
        });
        MDC.put("attemptId", ctx.jobId());
        tx.executeWithoutResult(s -> jobRepository.findById(ctx.jobId()).ifPresent(ScoringJob::markProcessing));
        long start = System.currentTimeMillis();
        try {
            QuestionDto question = attemptService.questionById(ctx.questionId());

            // 단계별 시간 — "내구성의 비용이 어디서 얼마"를 재기 위해. 동기 경로엔 없는 단계는 read(R2)와 save(중간 저장)뿐
            long tRead = 0, tStt = 0, tGrade, tSave;
            String speech = ctx.sttText();
            if (speech == null) {
                long t0 = System.currentTimeMillis();
                byte[] audio = audioStorage.read(ctx.audioKey());
                tRead = System.currentTimeMillis() - t0;
                t0 = System.currentTimeMillis();
                speech = feedbackService.transcribe(audio, "audio_" + ctx.questionIndex() + ".webm");
                tStt = System.currentTimeMillis() - t0;
                final String spoken = speech;
                // STT 성공분을 즉시 남긴다 — 이후 채점이 실패해 다시 집혀도 STT는 다시 안 부른다
                tx.executeWithoutResult(s -> itemRepository.findById(itemId).ifPresent(i -> i.rememberSpeech(spoken)));
            }

            long t1 = System.currentTimeMillis();
            FeedbackDTO feedback = feedbackService.gradeWithSpeech(speech, question);
            tGrade = System.currentTimeMillis() - t1;
            if (feedback.isFailed()) throw new IllegalStateException(feedback.getErrorMessage());

            long t2 = System.currentTimeMillis();
            tx.executeWithoutResult(s -> {
                ScoringJob job = jobRepository.findByIdForUpdate(ctx.jobId()).orElseThrow();   // 마무리 직렬화
                ScoringJobItem item = itemRepository.findById(itemId).orElseThrow();
                FeedbackResult saved = persistence.saveOne(feedback, job);
                item.markDone(saved == null ? null : saved.getId());
                job.refreshCompletion();
            });
            tSave = System.currentTimeMillis() - t2;
            circuit.record(true);
            meterRegistry.counter("opicnic.worker.items", "outcome", "done").increment();
            log.info("[Worker] 문항 {} DONE ({}ms = read {} + stt {} + grade {} + save {}, 시도 {})", ctx.questionIndex(),
                    System.currentTimeMillis() - start, tRead, tStt, tGrade, tSave, ctx.attempts());
        } catch (Exception e) {
            boolean rateLimited = FeedbackService.isRateLimited(e);
            Duration backoff = backoff(ctx.attempts(), rateLimited);
            tx.executeWithoutResult(s -> {
                ScoringJob job = jobRepository.findByIdForUpdate(ctx.jobId()).orElseThrow();
                ScoringJobItem item = itemRepository.findById(itemId).orElseThrow();
                item.markFailed(e.getMessage(), backoff);
                job.markProcessing();
                if (item.getStatus() == ScoringJobItemStatus.FAILED) job.refreshCompletion();
            });
            circuit.record(false);
            String outcome = ctx.attempts() >= ScoringJobItem.MAX_ATTEMPTS ? "failed" : "retry";
            meterRegistry.counter("opicnic.worker.items", "outcome", outcome).increment();
            log.warn("[Worker] 문항 {} 시도 {}/{} 실패{} → {} (backoff {}ms): {}", ctx.questionIndex(), ctx.attempts(),
                    ScoringJobItem.MAX_ATTEMPTS, rateLimited ? " (429)" : "", outcome, backoff.toMillis(), e.getMessage());
        } finally {
            MDC.clear();
        }
    }

    // 동기 경로(FeedbackService)와 같은 값: 429는 3s·6s + jitter, 그 외 1s·2s + jitter. 시도 n 뒤의 대기.
    static Duration backoff(int attempt, boolean rateLimited) {
        long base = rateLimited ? 3000L << (attempt - 1) : 1000L << (attempt - 1);
        long jitter = ThreadLocalRandom.current().nextLong(rateLimited ? 1000 : 300);
        return Duration.ofMillis(base + jitter);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private record ItemContext(Long itemId, String jobId, Long memberId, int questionIndex, Long questionId,
                               String audioKey, String sttText, int attempts) {
    }

    // ADR-0001 4절 "워커 루프에 전체 실패율 서킷". 최근 window건 중 실패 비율이 임계 이상이면 openFor 동안 집지 않는다 —
    // 모델 소멸(404)이나 제공자 장애 때 버그 하나로 아무도 안 보는 채 할당량을 다 태우는 것을 막는다.
    // 사람이 개입하는 자리: circuit_open 게이지가 1이면 알림. 자동 복구는 openFor 뒤 반열림(다시 집어봄).
    static final class FailureCircuit {
        private final boolean[] window;
        private final double failureRatio;
        private final Duration openFor;
        private int pos, filled, failures;
        private volatile LocalDateTime openUntil;

        FailureCircuit(int windowSize, double failureRatio, Duration openFor) {
            this.window = new boolean[Math.max(1, windowSize)];
            this.failureRatio = failureRatio;
            this.openFor = openFor;
        }

        synchronized void record(boolean success) {
            if (filled == window.length && !window[pos]) failures--;
            window[pos] = success;
            if (!success) failures++;
            pos = (pos + 1) % window.length;
            if (filled < window.length) filled++;
            if (filled == window.length && failures >= failureRatio * window.length && !isOpen()) {
                openUntil = LocalDateTime.now().plus(openFor);
                log.error("[Worker] 서킷 OPEN — 최근 {}건 중 실패 {}건. {}초 동안 집지 않음", window.length, failures, openFor.toSeconds());
                // 반열림 뒤 바로 다시 열리지 않게 창을 비운다
                java.util.Arrays.fill(window, true); failures = 0; filled = 0; pos = 0;
            }
        }

        boolean isOpen() {
            LocalDateTime until = openUntil;
            return until != null && LocalDateTime.now().isBefore(until);
        }
    }
}
