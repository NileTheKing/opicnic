package com.opicnic.opicnic.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor // 생성자 주입을 위해 사용
public class ComboQuestionStrategyFactory {

    // Spring이 자동으로 ComboQuestionStrategy 타입의 모든 빈을 주입해줍니다.
    // 키는 빈의 이름 (예: "fixedQuestionSelector", "opicStandardSelector")
    private final Map<String, ComboQuestionStrategy> questionSelectors;

    public ComboQuestionStrategy getQuestionSelector(String algorithm) {
        if (questionSelectors.containsKey(algorithm)) {
            return questionSelectors.get(algorithm);
        }
        // 요청된 알고리즘이 없을 경우, 기본 전략(예: "opicStandardSelector")을 반환하거나 예외 발생
        // 여기서는 "opicStandardSelector"를 기본값으로 가정
        ComboQuestionStrategy defaultStrategy = questionSelectors.get("opicStandardSelector");
        if (defaultStrategy == null) {
            // 기본 전략조차 없다면, 심각한 설정 오류이므로 예외 발생 또는 로깅
            throw new IllegalStateException("Default question selector 'opicStandardSelector' not found.");
        }
        return defaultStrategy;
    }
}