package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.Question;
import com.opicnic.opicnic.domain.QuestionSet;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class QuestionAssemblyService {

    private final QuestionSetRepository questionSetRepository;
    private final Random random;

    @Transactional(readOnly = true)
    public List<QuestionDto> assemble(SurveyTopic topic, ComboPattern pattern) {
        List<QuestionSet> sets = questionSetRepository.findByTopicWithDetails(topic);
        if (sets.isEmpty()) {
            throw new IllegalArgumentException("질문 세트를 찾을 수 없습니다. topic=" + topic);
        }

        QuestionSet set = sets.get(random.nextInt(sets.size()));
        return pattern.questionTypes().stream()
                .map(type -> findQuestion(set, type))
                .map(QuestionDto::from)
                .toList();
    }

    private Question findQuestion(QuestionSet set, QuestionType type) {
        return set.getQuestions().stream()
                .filter(question -> question.getQuestionType() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "질문 타입을 찾을 수 없습니다. set=" + set.getName() + ", type=" + type));
    }
}
