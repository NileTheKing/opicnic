package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.Combo;
import com.opicnic.opicnic.domain.QuestionSet;
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // 추가

import java.util.*;

@Service("opicStandardSelector") // 새로운 전략의 빈 이름
@RequiredArgsConstructor
@Primary // @Primary 어노테이션을 사용하여 기본 전략으로 설정
public class OpicStandardComboSelectionStrategy implements ComboQuestionStrategy {

    private final QuestionSetRepository questionSetRepository;
    private final Random random = new Random();

    @Override
    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public Optional<Combo> selectRandomCombo(SurveyTopic topic, SurveyDifficulty difficulty) {
        // 주제와 난이도에 맞는 세트들을 상세 정보(콤보, 질문)와 함께 조회
        List<QuestionSet> matchingSets = questionSetRepository.findByTopicAndDifficulty(topic, difficulty);

        if (matchingSets.isEmpty()) {
            // 매칭되는 세트가 없을 경우: 기본 돌발 문제 콤보를 반환하거나 예외 처리
            // 여기서는 빈 리스트를 반환하지만, 실제로는 돌발 문제 로직이 필요할 수 있습니다.
            System.err.println("No matching sets found for topic: " + topic + ", difficulty: " + difficulty + ". Consider adding 돌발/fallback logic.");
            return Optional.empty();
        }

        //무작위로 세트 고르기
        QuestionSet questionSet = matchingSets.get(random.nextInt(matchingSets.size()));
        //골라진 세트에서 콤보 하나 고르기
        List<Combo> combos = questionSet.getCombos();
        return Optional.ofNullable(combos.get(random.nextInt(combos.size())));
    }
}