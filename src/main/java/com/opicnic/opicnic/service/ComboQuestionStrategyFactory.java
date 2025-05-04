package com.opicnic.opicnic.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ComboQuestionStrategyFactory {

    private final Map<String, ComboQuestionStrategy> questionSelectors;

    public ComboQuestionStrategy getQuestionSelector(String algorithm) {
        if (questionSelectors.containsKey(algorithm)) {
            return questionSelectors.get(algorithm);
        }
        return questionSelectors.get("fixedQuestionSelector"); // 기본 전략으로 FixedQuestionSelector 반환
    }
}