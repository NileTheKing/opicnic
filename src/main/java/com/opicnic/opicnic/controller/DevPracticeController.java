package com.opicnic.opicnic.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/practice-attempts")
@RequiredArgsConstructor
@Slf4j
@Profile("dev")
public class DevPracticeController {

    private final PracticeAttemptService attemptService;
    private final FeedbackService feedbackService;
    private final ObjectMapper objectMapper;

    @PostMapping("/start")
    public ResponseEntity<?> startAttempt(@RequestParam String topic,
                                          @RequestParam String difficulty) {
        try {
            var combo = feedbackService.getComboQuestions(topic, difficulty);
            var attempt = attemptService.createAttempt(
                    combo.questions(), null, PracticeMode.COMBO,
                    combo.comboPatternKey(), combo.comboCategory());

            List<Integer> indexes = new ArrayList<>();
            for (int i = 0; i < combo.questions().size(); i++) indexes.add(i);

            return ResponseEntity.ok(Map.of(
                    "attemptId", attempt.attemptId(),
                    "questionIndexes", indexes,
                    "questionCount", combo.questions().size()
            ));
        } catch (Exception e) {
            log.error("attempt 생성 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @PostMapping("/answers/sequential-benchmark")
    public ResponseEntity<?> sequentialBenchmark(HttpServletRequest request) {
        try {
            String attemptId = request.getParameter("attemptId");
            String questionIndexesParam = request.getParameter("questionIndexes");
            List<Integer> questionIndexes = objectMapper.readValue(questionIndexesParam, new TypeReference<>() {});

            attemptService.requireValidAttempt(attemptId);
            List<QuestionDto> questions = attemptService.restoreQuestionsForIndexes(attemptId, questionIndexes);

            List<InputStream> streams = new ArrayList<>();
            for (var part : request.getParts()) {
                if ("files".equals(part.getName())) streams.add(part.getInputStream());
            }

            feedbackService.getComboFeedbackSequential(streams, questions);
            return ResponseEntity.ok("sequential benchmark done — check logs");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}
