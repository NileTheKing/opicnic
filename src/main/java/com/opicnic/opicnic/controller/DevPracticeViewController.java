package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.SurveyProfile;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.service.MockExamService;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

// dev 전용 화면 진입점. /practice/mock은 카카오 로그인이 필요해 브라우저에서 비동기 제출 흐름(question.html →
// R2 → /api/scoring-jobs → 결과 화면)을 손으로 확인하기 어렵다. DevPracticeController./start-mock의 화면 버전.
@Controller
@Profile("dev")
@RequiredArgsConstructor
public class DevPracticeViewController {

    private final MockExamService mockExamService;
    private final PracticeAttemptService attemptService;

    @GetMapping("/dev/practice/mock")
    public String mockExam(Model model) {
        SurveyProfile profile = SurveyProfile.builder()
                .preferredDifficulty(SurveyDifficulty.LEVEL_4).selectedTopics(List.of()).build();
        List<QuestionDto> questions = mockExamService.createMockExam(profile);
        var attempt = attemptService.createAttempt(questions, null, PracticeMode.MOCK_EXAM, null, null);
        model.addAttribute("questions", questions);
        model.addAttribute("attemptId", attempt.attemptId());
        model.addAttribute("asyncScoring", true);
        return "practice/question";
    }
}
