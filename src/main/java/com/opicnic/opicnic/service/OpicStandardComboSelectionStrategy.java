package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.Combo;
import com.opicnic.opicnic.domain.QuestionSet;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Primary
public class OpicStandardComboSelectionStrategy implements ComboQuestionStrategy {

    private final QuestionSetRepository questionSetRepository;
    private final Random random;

    // 레벨 3-4: [9,10] 타입 포함 콤보 제외
    // 레벨 5-6: [6,7,8] 타입 포함 콤보 제외
    private static final Set<QuestionType> LOWER_LEVEL_EXCLUDED = Set.of(QuestionType.TYPE_9, QuestionType.TYPE_10);
    private static final Set<QuestionType> UPPER_LEVEL_EXCLUDED = Set.of(QuestionType.TYPE_6, QuestionType.TYPE_7, QuestionType.TYPE_8);

    @Override
    @Transactional(readOnly = true)
    public Optional<Combo> selectRandomCombo(SurveyTopic topic, SurveyDifficulty difficulty) {
        List<QuestionSet> sets = questionSetRepository.findByTopicWithDetails(topic);
        if (sets.isEmpty()) return Optional.empty();

        QuestionSet set = sets.get(random.nextInt(sets.size()));

        Set<QuestionType> excluded = isUpperLevel(difficulty) ? UPPER_LEVEL_EXCLUDED : LOWER_LEVEL_EXCLUDED;
        List<Combo> validCombos = set.getCombos().stream()
                .filter(c -> c.getQuestionTypes().stream().noneMatch(excluded::contains))
                .toList();

        if (validCombos.isEmpty()) return Optional.empty();
        return Optional.of(validCombos.get(random.nextInt(validCombos.size())));
    }

    private boolean isUpperLevel(SurveyDifficulty difficulty) {
        return difficulty == SurveyDifficulty.LEVEL_5 || difficulty == SurveyDifficulty.LEVEL_6;
    }
}
