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

        List<ComboPattern> patterns = comboPatternProvider.getPatterns(difficulty);
        ComboPattern pattern = patterns.get(random.nextInt(patterns.size()));
        List<QuestionDto> questions = questionAssemblyService.assemble(topic, pattern);

        return new ComboQuestionsResult(
                pattern.name(),
                pattern.patternKey(),
                pattern.category(),
                questions
        );
    }
}
