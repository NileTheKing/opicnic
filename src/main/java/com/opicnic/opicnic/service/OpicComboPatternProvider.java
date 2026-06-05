package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.opicnic.opicnic.domain.enums.QuestionType.*;

@Component
public class OpicComboPatternProvider {

    public List<ComboPattern> getPatterns(SurveyDifficulty difficulty) {
        if (isUpperLevel(difficulty)) {
            return List.of(
                    new ComboPattern("C1", List.of(TYPE_1, TYPE_2, TYPE_3)),
                    new ComboPattern("C2", List.of(TYPE_1, TYPE_3, TYPE_4)),
                    new ComboPattern("C2", List.of(TYPE_1, TYPE_3, TYPE_4)),
                    new ComboPattern("C3", List.of(TYPE_6, TYPE_7, TYPE_8)),
                    new ComboPattern("C5", List.of(TYPE_9, TYPE_10))
            );
        }

        return List.of(
                new ComboPattern("C1", List.of(TYPE_1, TYPE_2, TYPE_3)),
                new ComboPattern("C1", List.of(TYPE_1, TYPE_2, TYPE_3)),
                new ComboPattern("C2", List.of(TYPE_1, TYPE_3, TYPE_4)),
                new ComboPattern("C3", List.of(TYPE_6, TYPE_7, TYPE_4)),
                new ComboPattern("C4", List.of(TYPE_1, TYPE_5))
        );
    }

    private boolean isUpperLevel(SurveyDifficulty difficulty) {
        return difficulty == SurveyDifficulty.LEVEL_5 || difficulty == SurveyDifficulty.LEVEL_6;
    }
}
