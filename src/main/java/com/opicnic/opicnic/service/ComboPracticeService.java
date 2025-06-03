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

        List<Combo> combos = comboQuestionStrategy.selectCombos(topic, difficulty);

        // 엔티티를 DTO로 변환하면서 topic과 difficulty 정보도 함께 추출합니다.
        return combos.stream()
                .flatMap(combo -> combo.getQuestions().stream())
                .map(question -> {
                    // QuestionSet 정보는 Combo를 통해 접근 가능 (Lazy 로딩 주의)
                    // @BatchSize를 Combo 및 Question에 추가했으므로, 여기서는 N+1 문제가 크게 발생하지 않습니다.
                    String questionTopic = question.getCombo().getQuestionSet().getTopic().getLabel(); // 또는 .name()
                    String questionDifficulty = question.getCombo().getQuestionSet().getDifficulty().getLabel(); // 또는 .name()

                    return new QuestionDto(
                            question.getId(),
                            question.getContent(),
                            questionTopic,
                            questionDifficulty
                    );
                })
                .collect(Collectors.toList());
    }
}