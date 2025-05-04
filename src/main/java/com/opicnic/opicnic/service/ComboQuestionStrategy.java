package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.Question;

import java.util.List;

public interface ComboQuestionStrategy {
    List<Question> selectQuestions(String topic, String difficulty);
}
