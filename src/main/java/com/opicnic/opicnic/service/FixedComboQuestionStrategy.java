package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.*; // 모든 도메인 엔티티 임포트
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service("fixedQuestionSelector") // 빈 이름 유지
public class FixedComboQuestionStrategy implements ComboQuestionStrategy {

    @Override
    public Optional<Combo> selectRandomCombo(SurveyTopic topic, SurveyDifficulty difficulty) {
        // 이 "고정된" 전략은 이 예제에서는 입력된 주제/난이도를 무시하고,
        // 미리 정의된 하나의 콤보를 반환합니다.
        // 실제 DB 기반의 고정 전략은 특정 ID로 엔티티를 조회할 수 있습니다.

        // 임시 QuestionSet 생성 (이 전략 내에서는 DB에 저장되지 않음)
        QuestionSet fixedSet = new QuestionSet("Fixed Park Sample Set", SurveyDifficulty.LEVEL_3, SurveyTopic.PARK_GOING);
        fixedSet.setId(999L); // 임시 ID

        // 임시 Combo 생성
        Combo fixedCombo = new Combo("Fixed Park Combo for Practice", fixedSet, 1);
        fixedCombo.setId(888L); // 임시 ID

        // 이 콤보에 포함될 임시 Question 객체들 생성 (영어로 된 문제)
        Question q1 = new Question("Please describe a park you often visit.", 1, fixedCombo);
        q1.setId(701L); // 임시 ID
        Question q2 = new Question("Tell me about your most recent visit to a park.", 2, fixedCombo);
        q2.setId(702L);
        Question q3 = new Question("What is your most memorable experience in a park?", 3, fixedCombo);
        q3.setId(703L);

        // 질문들을 콤보에 추가
        fixedCombo.getQuestions().addAll(List.of(q1, q2, q3));

        // 생성된 콤보를 결과 목록에 추가

        return Optional.of(fixedCombo);
    }
}