package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.Combo; // 새로운 Combo 엔티티 임포트
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;

import java.util.List;
import java.util.Optional;

public interface ComboQuestionStrategy {
    // 반환 타입을 List<Question>에서 List<Combo>로 변경
    // 파라미터 타입을 String에서 Enum으로 변경하여 타입 안정성 확보
    Optional<Combo> selectRandomCombo(SurveyTopic topic, SurveyDifficulty difficulty);
}