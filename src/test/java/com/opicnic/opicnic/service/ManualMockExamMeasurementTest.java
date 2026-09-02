package com.opicnic.opicnic.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 수동 측정 전용. 모의고사 15문항 일괄 제출(FeedbackService.getComboFeedbackStreaming) 1회의
// 실제 처리 특성(전체 시간/문항별 시간/실패/429/재시도/힙/STT 텍스트)을 재는 것이 목적이다.
// -Dmanual.mockexam=true 로만 켜진다. 실제 Groq API를 호출하므로 임의 실행 금지.
@SpringBootTest
@Testcontainers
@EnabledIfSystemProperty(named = "manual.mockexam", matches = "true")
public class ManualMockExamMeasurementTest {

    private static final int QUESTION_COUNT = 15;
    private static final Path AUDIO = Path.of("scripts/test_1m20s.webm");

    @Autowired
    private FeedbackService feedbackService;

    @Container
    public static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Test
    @DisplayName("모의고사 15문항 일괄 제출 1회 실측")
    void measure() throws Exception {
        byte[] audio = Files.readAllBytes(AUDIO);
        List<byte[]> audioBuffers = new ArrayList<>();
        List<QuestionDto> questions = new ArrayList<>();
        QuestionType[] types = QuestionType.values();

        for (int i = 0; i < QUESTION_COUNT; i++) {
            audioBuffers.add(audio);
            if (i == 0) {
                questions.add(new QuestionDto(null, "Let's start the interview. Tell me about yourself.",
                        "자기소개", null));
            } else {
                QuestionType type = types[(i - 1) % types.length];
                questions.add(new QuestionDto((long) i,
                        "Question " + i + " (" + type.name() + "): Please describe it in detail.",
                        "측정용 주제", type));
            }
        }

        Logger feedbackLogger = (Logger) LoggerFactory.getLogger(FeedbackService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        feedbackLogger.setLevel(Level.INFO);
        feedbackLogger.addAppender(appender);

        System.gc();
        Thread.sleep(300);
        Runtime rt = Runtime.getRuntime();
        long usedBefore = rt.totalMemory() - rt.freeMemory();

        List<FeedbackDTO> results = null;
        Exception thrown = null;
        long start = System.currentTimeMillis();
        try {
            results = feedbackService.getComboFeedbackStreaming(audioBuffers, questions);
        } catch (Exception e) {
            thrown = e;
        }
        long totalMs = System.currentTimeMillis() - start;
        long usedAfter = rt.totalMemory() - rt.freeMemory();

        feedbackLogger.detachAppender(appender);
        List<ILoggingEvent> events = new ArrayList<>(appender.list);

        // 문항별 시간: FeedbackService의 "[Subtask-N] 완료: Xms" / "[Subtask-N] 최종 실패 (...): Xms" 로그에서 추출.
        // 자기소개(index 0)와 무응답 조기 반환 경로는 이 로그를 남기지 않으므로 N/A로 나온다.
        Map<Integer, String> perQuestion = new LinkedHashMap<>();
        Pattern donePattern = Pattern.compile("\\[Subtask-(\\d+)] 완료: (\\d+)ms(.*)");
        Pattern failPattern = Pattern.compile("\\[Subtask-(\\d+)] 최종 실패 \\((\\d+)회 시도\\): (\\d+)ms");
        Map<Integer, Long> startedAt = new LinkedHashMap<>();
        Pattern startPattern = Pattern.compile("\\[Subtask-(\\d+)] STT & LLM 처리 시작");

        int rateLimitHits = 0;
        int retryEvents = 0;
        List<String> failureMessages = new ArrayList<>();

        for (ILoggingEvent e : events) {
            String msg = e.getFormattedMessage();
            Matcher m = startPattern.matcher(msg);
            if (m.find()) {
                startedAt.put(Integer.parseInt(m.group(1)), e.getTimeStamp());
            }
            m = donePattern.matcher(msg);
            if (m.find()) {
                perQuestion.put(Integer.parseInt(m.group(1)), m.group(2) + "ms" + m.group(3));
                continue;
            }
            m = failPattern.matcher(msg);
            if (m.find()) {
                perQuestion.put(Integer.parseInt(m.group(1)), m.group(3) + "ms (최종 실패)");
                failureMessages.add(msg);
                continue;
            }
            if (msg.contains("(429 rate limit)")) rateLimitHits++;
            if (msg.contains("재시도") && msg.contains("대기")) retryEvents++;
        }

        StringBuilder sb = new StringBuilder();
        String line = "=".repeat(78);
        sb.append("\n").append(line).append("\n");
        sb.append("### MOCKEXAM-15 MEASUREMENT ###\n");
        sb.append(line).append("\n");
        sb.append("STT_ENABLED=").append(System.getProperty("STT_ENABLED", String.valueOf(System.getenv("STT_ENABLED"))))
          .append("  LLM_ENABLED=").append(System.getProperty("LLM_ENABLED", String.valueOf(System.getenv("LLM_ENABLED"))))
          .append("\n");
        sb.append("audio: ").append(AUDIO).append(" (").append(audio.length).append(" bytes) x ").append(QUESTION_COUNT).append("\n");
        sb.append("maxHeap=").append(rt.maxMemory() / 1024 / 1024).append("MB\n\n");

        sb.append("[1] 전체 소요 시간: ").append(totalMs).append(" ms");
        if (thrown != null) sb.append("  (예외로 종료: ").append(thrown.getClass().getSimpleName())
                .append(": ").append(thrown.getMessage()).append(")");
        sb.append("\n\n");

        sb.append("[2] 문항별 소요 시간 (FeedbackService 로그 기준)\n");
        for (int i = 0; i < QUESTION_COUNT; i++) {
            String type = questions.get(i).getQuestionType() == null ? "SELF_INTRO" : questions.get(i).getQuestionType().name();
            String dur = perQuestion.getOrDefault(i, "N/A (완료 로그 없음 - 자기소개/무응답 조기반환 또는 미완료)");
            Long st = startedAt.get(i);
            sb.append(String.format("  q%-2d %-11s start=+%-6s %s%n", i, type,
                    st == null ? "?" : (st - start) + "ms", dur));
        }
        sb.append("\n");

        int ok = 0, failed = 0;
        if (results != null) {
            for (int i = 0; i < results.size(); i++) {
                FeedbackDTO d = results.get(i);
                if (d.isFailed()) {
                    failed++;
                    failureMessages.add("q" + i + " failed: " + d.getErrorMessage());
                } else ok++;
            }
        }
        sb.append("[3] 성공/실패 문항 수: 성공 ").append(ok).append(" / 실패 ").append(failed);
        if (results == null) sb.append("  (결과 리스트 없음 - 호출 자체가 예외로 종료)");
        sb.append("\n");
        for (String f : failureMessages) sb.append("     - ").append(f).append("\n");
        sb.append("\n");

        sb.append("[4] 429 rate limit 감지 건수: ").append(rateLimitHits).append("\n");
        sb.append("[5] 재시도 발생 건수: ").append(retryEvents).append("\n\n");

        sb.append("[6] 힙 사용량 (참고값 - GC 타이밍 때문에 정밀하지 않음)\n");
        sb.append("     호출 전 used: ").append(usedBefore / 1024 / 1024).append(" MB\n");
        sb.append("     호출 직후 used: ").append(usedAfter / 1024 / 1024).append(" MB\n");
        sb.append("     증가분: ").append((usedAfter - usedBefore) / 1024 / 1024).append(" MB\n\n");

        sb.append("[7] STT 결과 텍스트 (샘플)\n");
        if (results != null) {
            for (int i = 0; i < results.size(); i++) {
                String t = results.get(i).getSttText();
                if (t != null) {
                    sb.append("     q").append(i).append(" length=").append(t.length())
                      .append(" words=").append(t.trim().split("\\s+").length).append("\n");
                    sb.append("     preview: ").append(t.substring(0, Math.min(200, t.length()))).append("\n");
                    break;
                }
            }
        } else {
            sb.append("     N/A\n");
        }
        sb.append(line).append("\n");

        System.out.println(sb);

        if (thrown != null) throw thrown;
    }
}
