package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.AttemptStatus;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.MockExamService;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// dev 전용 POST /api/practice-attempts/start-mock 회귀 테스트 — S1(모의고사 15문항) 측정 전제.
// 기존 /start(콤보) 테스트가 없어 HomeControllerSurprisePracticeTest처럼 컨트롤러를 직접 생성해 검증한다.
class DevPracticeControllerStartMockTest {

    @Test
    void startMockAttempt_returns15QuestionMockExam() {
        PracticeAttemptService attemptService = Mockito.mock(PracticeAttemptService.class);
        FeedbackService feedbackService = Mockito.mock(FeedbackService.class);
        MockExamService mockExamService = Mockito.mock(MockExamService.class);

        List<QuestionDto> questions = new ArrayList<>();
        questions.add(new QuestionDto(null, "Please introduce yourself.", "자기소개", null));
        for (int i = 0; i < 14; i++) {
            questions.add(new QuestionDto((long) i, "content " + i, "topic", QuestionType.TYPE_1));
        }
        when(mockExamService.createMockExam(any())).thenReturn(questions);

        PracticeAttempt attempt = new PracticeAttempt(
                "attempt-1", questions.stream().map(QuestionDto::getId).toList(), null, PracticeMode.MOCK_EXAM,
                null, null, Instant.now().plus(2, ChronoUnit.HOURS), AttemptStatus.IN_PROGRESS);
        when(attemptService.createAttempt(any(), any(), any(), any(), any())).thenReturn(attempt);

        DevPracticeController controller = new DevPracticeController(attemptService, feedbackService, mockExamService);

        ResponseEntity<?> response = controller.startMockAttempt();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("attemptId")).isEqualTo("attempt-1");
        assertThat(body.get("questionCount")).isEqualTo(15);
        assertThat((List<?>) body.get("questionIndexes")).hasSize(15);

        ArgumentCaptor<PracticeMode> modeCaptor = ArgumentCaptor.forClass(PracticeMode.class);
        Mockito.verify(attemptService).createAttempt(any(), Mockito.isNull(), modeCaptor.capture(), any(), any());
        assertThat(modeCaptor.getValue()).isEqualTo(PracticeMode.MOCK_EXAM);
    }
}
