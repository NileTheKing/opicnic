package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.Combo;
import com.opicnic.opicnic.domain.Question;
import com.opicnic.opicnic.domain.QuestionSet;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service("fixedQuestionSelector")
public class FixedComboQuestionStrategy implements ComboQuestionStrategy {

    @Override
    public Optional<Combo> selectRandomCombo(SurveyTopic topic, SurveyDifficulty difficulty) {
        QuestionSet fixedSet = new QuestionSet("Fixed Park Sample Set", SurveyTopic.PARK_GOING);
        fixedSet.setId(999L);

        fixedSet.getQuestions().addAll(List.of(
            new Question("Describe a park you often visit.", QuestionType.TYPE_1, fixedSet),
            new Question("What is your typical routine when you visit the park?", QuestionType.TYPE_2, fixedSet),
            new Question("Tell me about a recent trip to the park.", QuestionType.TYPE_3, fixedSet),
            new Question("What is the most memorable experience you have had at a park?", QuestionType.TYPE_4, fixedSet),
            new Question("Imagine you are planning a park visit with a friend.", QuestionType.TYPE_5, fixedSet),
            new Question("Ask a park staff member about facilities and events.", QuestionType.TYPE_6, fixedSet),
            new Question("There is a problem at the park. Suggest alternatives.", QuestionType.TYPE_7, fixedSet),
            new Question("Tell me about a similar outdoor experience you have had.", QuestionType.TYPE_8, fixedSet),
            new Question("Compare parks today versus parks in the past.", QuestionType.TYPE_9, fixedSet),
            new Question("What are some issues related to public parks today?", QuestionType.TYPE_10, fixedSet)
        ));

        Combo fixedCombo = new Combo("Fixed Park Combo", fixedSet,
                List.of(QuestionType.TYPE_1, QuestionType.TYPE_2, QuestionType.TYPE_3));
        fixedCombo.setId(888L);

        return Optional.of(fixedCombo);
    }
}
