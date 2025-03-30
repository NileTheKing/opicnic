package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.Question;

import java.util.List;

public interface QuestionSelector {
    List<Question> selectQuestions(String topic, String difficulty);
}
