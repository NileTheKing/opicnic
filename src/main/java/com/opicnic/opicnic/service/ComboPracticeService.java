// src/main/java/com/opicnic/opicnic/service/ComboPracticeService.java

package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.Combo;
import com.opicnic.opicnic.domain.Question;
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.dto.QuestionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // @Transactional 추가

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComboPracticeService {

    private final ComboQuestionStrategy comboQuestionStrategy;

    @Transactional(readOnly = true) // 데이터베이스 조회 시 @Transactional(readOnly = true) 권장
    public List<QuestionDto> getComboQuestions(String topicStr, String difficultyStr) {
        SurveyTopic topic = SurveyTopic.valueOf(topicStr);
        SurveyDifficulty difficulty = SurveyDifficulty.valueOf(difficultyStr);

        Combo combo = comboQuestionStrategy.selectRandomCombo(topic, difficulty)
                .orElseThrow(() -> new IllegalArgumentException("해당 조건에 맞는 콤보를 찾을 수 없습니다. Topic: " + topic + ", Difficulty: " + difficulty));

        // 엔티티를 DTO로 변환하면서 topic과 difficulty 정보도 함께 추출합니다.
        return combo.getQuestions().stream()
                .map(QuestionDto::from)
                .collect(Collectors.toList());
    }
}