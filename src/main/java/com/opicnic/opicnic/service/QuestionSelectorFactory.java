package com.opicnic.opicnic.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class QuestionSelectorFactory {

    private final Map<String, QuestionSelector> questionSelectors;

    public QuestionSelector getQuestionSelector(String algorithm) {
        if (questionSelectors.containsKey(algorithm)) {
            return questionSelectors.get(algorithm);
        }
        return questionSelectors.get("fixedQuestionSelector"); // 기본 전략으로 FixedQuestionSelector 반환
    }
}