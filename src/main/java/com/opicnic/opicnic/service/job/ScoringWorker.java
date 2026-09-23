package com.opicnic.opicnic.service.job;

import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.job.ScoringJob;
import com.opicnic.opicnic.domain.job.ScoringJobItem;
import com.opicnic.opicnic.domain.job.ScoringJobItem.FailureKind;
import com.opicnic.opicnic.domain.job.ScoringJobItemStatus;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.ScoringJobItemRepository;
import com.opicnic.opicnic.repository.ScoringJobRepository;
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.attempt.FeedbackPersistenceService;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import com.opicnic.opicnic.storage.AudioStorage;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.springframework.web.client.HttpClientErrorException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// ADR-0001 4절 ④. DB(잡 테이블)를 큐로 쓰는 워커. 서버·컨트롤러와 직접 대화하지 않고 DB로만 만난다 —
// 그래서 재시작하면 QUEUED/PROCESSING 행을 다시 주워 이어간다.
//
// 한 문항 처리: claim → R2 읽기 → STT(성공분은 DB에 남김) → 채점·태깅 → [FeedbackResult 저장 + DONE]을 한 트랜잭션.
// 실패: 분류(classify) → 일시적이면 접수 후 retryBudget(30분)까지 백오프 뒤 QUEUED(다시 집힘), 영구적이면 즉시 FAILED,
// LLM 형식 오류면 3회째 FAILED. 재시도는 "다시 집기"다. 내부 루프 없음.
// 예산이 횟수가 아니라 시간인 이유: 사용자는 요청에 묶여 있지 않고(비동기), 실패는 지연보다 훨씬 비싸다(다시 말해야 함).
// 3회 = 약 10초이던 동기 시절 상한은 20초짜리 제공자 흔들림에도 문항을 영구 실패로 만들었다 (docs/performance/slo.md).
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
    private final Duration retryBudget;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicLong queued = new AtomicLong();

    public ScoringWorker(ScoringJobRepository jobRepository, ScoringJobItemRepository itemRepository,
                         PracticeAttemptService attemptService, FeedbackService feedbackService,
                         FeedbackPersistenceService persistence, AudioStorage audioStorage,
                         TransactionTemplate tx, MeterRegistry meterRegistry,
                         @Value("${opicnic.worker.enabled:true}") boolean enabled,
                         @Value("${opicnic.worker.concurrency:60}") int concurrency,
                         @Value("${opicnic.worker.circuit.window:20}") int circuitWindow,
                         @Value("${opicnic.worker.circuit.failure-ratio:0.8}") double circuitFailureRatio,
                         @Value("${opicnic.worker.circuit.open-seconds:30}") long circuitOpenSeconds,
                         @Value("${opicnic.worker.retry-budget:30m}") Duration retryBudget) {
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
        this.retryBudget = retryBudget;
        this.circuit = new FailureCircuit(circuitWindow, circuitFailureRatio, Duration.ofSeconds(circuitOpenSeconds));
        meterRegistry.gauge("opicnic.worker.in_flight", inFlight);
        meterRegistry.gauge("opicnic.worker.circuit_open", circuit, c -> c.isOpen() ? 1 : 0);
        meterRegistry.gauge("opicnic.worker.queued", queued);   // 큐 깊이(QUEUED 수). poll()마다 갱신 — 1초 지연의 대시보드용 값
        log.info("[Worker] enabled={} concurrency={} retryBudget={} circuit(window={}, ratio={}, open={}s)",
                enabled, concurrency, retryBudget, circuitWindow, circuitFailureRatio, circuitOpenSeconds);
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

        if (circuit.isOpen() || slots.availablePermits() == 0) {
            queued.set(itemRepository.countByStatus(ScoringJobItemStatus.QUEUED));
            return;
        }
        int free = slots.availablePermits();

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
        queued.set(itemRepository.countByStatus(ScoringJobItemStatus.QUEUED));   // claim 뒤에 세야 "대기"와 "처리 중"이 겹치지 않는다
    }

    // 트랜잭션은 DB를 만지는 짧은 구간에만. Groq를 기다리는 수 초 동안 커넥션을 물고 있지 않는다.
    void process(Long itemId) {
        ItemContext ctx = tx.execute(s -> {
            ScoringJobItem item = itemRepository.findById(itemId).orElseThrow();
            ScoringJob job = item.getJob();
            return new ItemContext(item.getId(), job.getId(), job.getMember().getId(), item.getQuestionIndex(),
                    item.getQuestionId(), item.getAudioKey(), item.getSttText(), item.getAttempts(),
                    job.getCreatedAt().plus(retryBudget));
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
                recordJobDurationIfFinished(job);
            });
            tSave = System.currentTimeMillis() - t2;
            circuit.record(true);
            meterRegistry.counter("opicnic.worker.items", "outcome", "done").increment();
            log.info("[Worker] 문항 {} DONE ({}ms = read {} + stt {} + grade {} + save {}, 시도 {})", ctx.questionIndex(),
                    System.currentTimeMillis() - start, tRead, tStt, tGrade, tSave, ctx.attempts());
        } catch (Exception e) {
            boolean rateLimited = FeedbackService.isRateLimited(e);
            FailureKind kind = classify(e);
            Duration backoff = backoff(ctx.attempts(), rateLimited);
            ScoringJobItemStatus after = tx.execute(s -> {
                ScoringJob job = jobRepository.findByIdForUpdate(ctx.jobId()).orElseThrow();
                ScoringJobItem item = itemRepository.findById(itemId).orElseThrow();
                item.markFailed(e.getMessage(), kind, backoff, ctx.deadline());
                job.markProcessing();
                if (item.getStatus() == ScoringJobItemStatus.FAILED) {
                    job.refreshCompletion();
                    recordJobDurationIfFinished(job);
                }
                return item.getStatus();
            });
            // 녹음 파일 문제는 제공자 상태와 무관하다 — 서킷(제공자 장애 감지)에 섞지 않는다
            if (kind != FailureKind.PERMANENT) circuit.record(false);
            String outcome = after == ScoringJobItemStatus.FAILED ? "failed" : "retry";
            meterRegistry.counter("opicnic.worker.items", "outcome", outcome).increment();
            meterRegistry.counter("opicnic.worker.failures", "kind", kind.name().toLowerCase()).increment();
            if ("retry".equals(outcome)) {
                // S2(호출 증폭) 관측용. 옛 동기 루프의 opicnic.retry와 같은 이름·태그 — 대시보드 "재시도 횟수" 패널이 그대로 읽는다
                meterRegistry.counter("opicnic.retry",
                        "kind", ctx.sttText() == null ? "stt" : "llm",
                        "reason", rateLimited ? "429" : "other").increment();
            }
            log.warn("[Worker] 문항 {} 시도 {} 실패 [{}{}] → {} (backoff {}ms, 예산 {}까지): {}", ctx.questionIndex(),
                    ctx.attempts(), kind, rateLimited ? ", 429" : "", outcome, backoff.toMillis(), ctx.deadline(),
                    e.getMessage());
        } finally {
            MDC.clear();
        }
    }

    // 접수(created_at) → 마지막 문항 종료(completed_at). SLO "접수 후 완료 시간"의 실제 값. 잡을 닫은 트랜잭션 안에서 한 번만 기록된다
    private void recordJobDurationIfFinished(ScoringJob job) {
        if (!job.isFinished() || job.getCompletedAt() == null) return;
        meterRegistry.timer("opicnic.job.duration", "status", job.getStatus().name())
                .record(Duration.between(job.getCreatedAt(), job.getCompletedAt()));
    }

    // 시도 n 뒤의 대기: 2s·4s·8s…(429는 4s부터) 2배씩, 상한 60s, + jitter. 상한이 있어 30분 예산을 다 써도
    // 문항당 호출은 분당 한 번 꼴이고, 제공자가 완전히 죽었으면 서킷이 집기 자체를 멈춘다.
    static Duration backoff(int attempt, boolean rateLimited) {
        long base = rateLimited ? 4000L : 2000L;
        long delay = Math.min(MAX_BACKOFF_MS, base << Math.min(attempt - 1, 10));
        long jitter = ThreadLocalRandom.current().nextLong(1000);
        return Duration.ofMillis(delay + jitter);
    }

    static final long MAX_BACKOFF_MS = 60_000L;

    // 기다리면 풀리는가로 나눈다.
    //  PERMANENT: 녹음 파일이 스토리지에 없음, 제공자가 파일 자체를 거절(400·413·415·422) — 몇 번을 해도 같다
    //  INVALID_OUTPUT: LLM 응답이 형식을 어김(점수 누락·범위 밖, JSON 파싱 실패) — 몇 번은 다시 해볼 만하다
    //  TRANSIENT: 나머지(429·5xx·타임아웃·제공자 인증/모델 문제) — 운영자가 고치면 풀리므로 예산까지 기다린다
    static FailureKind classify(Throwable e) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof NoSuchKeyException || c instanceof java.util.NoSuchElementException) return FailureKind.PERMANENT;
            if (c instanceof HttpClientErrorException http) {
                int code = http.getStatusCode().value();
                if (code == 400 || code == 413 || code == 415 || code == 422) return FailureKind.PERMANENT;
            }
            if (c instanceof IllegalStateException || c instanceof JsonProcessingException) return FailureKind.INVALID_OUTPUT;
        }
        return FailureKind.TRANSIENT;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private record ItemContext(Long itemId, String jobId, Long memberId, int questionIndex, Long questionId,
                               String audioKey, String sttText, int attempts, LocalDateTime deadline) {
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
