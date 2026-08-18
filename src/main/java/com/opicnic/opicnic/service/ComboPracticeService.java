package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.dto.ComboQuestionsResult;
import com.opicnic.opicnic.dto.QuestionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class ComboPracticeService {

    private final OpicComboPatternProvider comboPatternProvider;
    private final QuestionAssemblyService questionAssemblyService;
    private final Random random;

    public ComboQuestionsResult getComboQuestions(String topicStr, String difficultyStr) {
        SurveyTopic topic = SurveyTopic.valueOf(topicStr);
        SurveyDifficulty difficulty = SurveyDifficulty.valueOf(difficultyStr);
        return buildResult(topic, difficulty, null);
    }

    public ComboQuestionsResult getComboQuestionsByCategory(SurveyTopic topic, SurveyDifficulty difficulty, String category) {
        return buildResult(topic, difficulty, category);
    }

    private ComboQuestionsResult buildResult(SurveyTopic topic, SurveyDifficulty difficulty, String category) {
        List<ComboPattern> patterns = comboPatternProvider.getPatterns(difficulty);
        if (category != null) {
            patterns = patterns.stream()
                    .filter(p -> p.category().equals(category))
                    .toList();
            // 난이도에 없는 category로 조용히 다른 category를 대신 내주지 않는다 (PC-04).
            // 예: LEVEL_3~4에는 C5가, LEVEL_5~6에는 C4가 없다.
            if (patterns.isEmpty()) {
                throw new IllegalStateException(
                        "difficulty=" + difficulty + "에서 지원하지 않는 category=" + category + "입니다.");
            }
        }
        ComboPattern pattern = patterns.get(random.nextInt(patterns.size()));
        List<QuestionDto> questions = questionAssemblyService.assemble(topic, pattern);
        return new ComboQuestionsResult(pattern.name(), pattern.patternKey(), pattern.category(), questions);
    }
}
