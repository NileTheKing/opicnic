package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.Combo;
import com.opicnic.opicnic.domain.Question;
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import lombok.extern.slf4j.Slf4j; // 로깅 추가
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // 추가

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j // 로깅을 위한 Lombok 어노테이션
public class ComboPracticeService {

    private final ComboQuestionStrategyFactory questionStrategyFactory; // 명확성을 위해 이름 변경

    @Autowired
    public ComboPracticeService(ComboQuestionStrategyFactory questionStrategyFactory) {
        this.questionStrategyFactory = questionStrategyFactory;
    }

    @Transactional(readOnly = true) // 읽기 작업이므로 readOnly 트랜잭션
    public List<Question> getComboQuestions(String topicStr, String difficultyStr, String algorithm) {
        SurveyTopic topic;
        SurveyDifficulty difficulty;

        // 입력된 문자열을 Enum으로 변환 (대소문자 구분 없이, 오류 처리 강화)
        try {
            topic = SurveyTopic.valueOf(topicStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("Invalid topic string: '{}'. Valid topics are: {}", topicStr, SurveyTopic.values());
            return Collections.emptyList(); // 유효하지 않은 주제면 빈 리스트 반환
        }

        try {
            // 난이도 문자열 예시: "LEVEL_3", "level-3", "3"
            String normalizedDifficultyStr = difficultyStr.trim().toUpperCase().replace("-", "_");
            if (!normalizedDifficultyStr.startsWith("LEVEL_")) {
                // 사용자가 "3"과 같이 숫자만 입력한 경우 "LEVEL_3"으로 변환 시도
                if (normalizedDifficultyStr.matches("\\d+")) {
                    normalizedDifficultyStr = "LEVEL_" + normalizedDifficultyStr;
                } else { // 그 외의 형식은 지원하지 않음
                    throw new IllegalArgumentException("Difficulty string must be in a recognized format (e.g., LEVEL_3 or 3).");
                }
            }
            difficulty = SurveyDifficulty.valueOf(normalizedDifficultyStr);
        } catch (IllegalArgumentException e) {
            log.error("Invalid difficulty string: '{}'. Valid difficulties are like: LEVEL_1, LEVEL_2, etc.", difficultyStr);
            return Collections.emptyList(); // 유효하지 않은 난이도면 빈 리스트 반환
        }

        log.info("Requesting combo questions for Topic: {}, Difficulty: {}, Algorithm: {}", topic, difficulty, algorithm);

        ComboQuestionStrategy questionSelector = questionStrategyFactory.getQuestionSelector(algorithm);
        List<Combo> selectedCombos = questionSelector.selectCombos(topic, difficulty);

        if (selectedCombos.isEmpty()) {
            log.warn("No combos selected for Topic: {}, Difficulty: {}, Algorithm: {}", topic, difficulty, algorithm);
            return Collections.emptyList();
        }

        // 선택된 Combo 리스트에서 Question 리스트를 추출하여 하나의 리스트로 만듦
        return selectedCombos.stream()
                .flatMap(combo -> {
                    if (combo.getQuestions() == null) { // 방어 코드
                        log.warn("Combo with ID {} has null questions list.", combo.getId());
                        return Stream.empty();
                    }
                    return combo.getQuestions().stream();
                })
                .collect(Collectors.toList());
    }
}