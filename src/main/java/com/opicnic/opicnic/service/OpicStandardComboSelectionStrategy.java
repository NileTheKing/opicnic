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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service("opicStandardSelector") // 새로운 전략의 빈 이름
@RequiredArgsConstructor
@Primary // @Primary 어노테이션을 사용하여 기본 전략으로 설정
public class OpicStandardComboSelectionStrategy implements ComboQuestionStrategy {

    private final QuestionSetRepository questionSetRepository;
    private final Random random = new Random();

    @Override
    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public List<Combo> selectCombos(SurveyTopic topic, SurveyDifficulty difficulty) {
        // 주제와 난이도에 맞는 세트들을 상세 정보(콤보, 질문)와 함께 조회
        List<QuestionSet> matchingSets = questionSetRepository.findByTopicAndDifficulty(topic, difficulty);
        List<Combo> selectedCombos = new ArrayList<>();

        if (matchingSets.isEmpty()) {
            // 매칭되는 세트가 없을 경우: 기본 돌발 문제 콤보를 반환하거나 예외 처리
            // 여기서는 빈 리스트를 반환하지만, 실제로는 돌발 문제 로직이 필요할 수 있습니다.
            System.err.println("No matching sets found for topic: " + topic + ", difficulty: " + difficulty + ". Consider adding 돌발/fallback logic.");
            return Collections.emptyList();
        }

        if (matchingSets.size() >= 2) {
            // 매칭된 세트가 2개 이상일 경우, 랜덤으로 하나의 세트를 선택
            QuestionSet chosenSet = matchingSets.get(random.nextInt(matchingSets.size()));
            // 선택된 세트의 모든 콤보를 반환 (또는 특정 규칙에 따라 일부 콤보)
            selectedCombos.addAll(chosenSet.getCombos());
        } else { // 매칭된 세트가 정확히 1개일 경우
            QuestionSet singleSet = matchingSets.get(0);
            List<Combo> combosInSet = new ArrayList<>(singleSet.getCombos()); // 프록시 초기화를 위해 새 리스트에 담기
            if (!combosInSet.isEmpty()) {
                // 세트 내 콤보들을 섞음
                Collections.shuffle(combosInSet);
                // 정책: 1개의 세트만 매칭된 경우, 해당 세트에서 랜덤으로 1개의 콤보를 선택
                selectedCombos.add(combosInSet.get(0));
                // 또는, 최대 2개의 콤보를 선택하는 등의 다른 정책 적용 가능
                // selectedCombos.addAll(combosInSet.subList(0, Math.min(combosInSet.size(), 2)));
            }
        }
        return selectedCombos;
    }
}