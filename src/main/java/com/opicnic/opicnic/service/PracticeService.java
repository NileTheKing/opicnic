package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.Question;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PracticeService {

    private final QuestionSelectorFactory questionSelectorFactory;

    @Autowired
    public PracticeService(QuestionSelectorFactory questionSelectorFactory) {
        this.questionSelectorFactory = questionSelectorFactory;
    }

    public List<Question> getComboQuestions(String topic, String difficulty, String algorithm) {
        QuestionSelector questionSelector = questionSelectorFactory.getQuestionSelector(algorithm);
        return questionSelector.selectQuestions(topic, difficulty);
    }

}