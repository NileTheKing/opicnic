package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.Question;
import com.opicnic.opicnic.domain.QuestionSet;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionAssemblyService {

    private final QuestionSetRepository questionSetRepository;
    private final Random random;

    private final Map<SurveyTopic, List<QuestionSet>> setCache = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public List<QuestionDto> assemble(SurveyTopic topic, ComboPattern pattern) {
        boolean cacheHit = setCache.containsKey(topic);
        long start = System.currentTimeMillis();
        List<QuestionSet> sets = setCache.computeIfAbsent(
                topic, questionSetRepository::findByTopicWithDetails);
        long elapsed = System.currentTimeMillis() - start;
        log.info("[QuestionSet 캐시] topic={} | {} | {}ms",
                topic, cacheHit ? "HIT" : "MISS(DB)", elapsed);
        if (sets.isEmpty()) {
            throw new IllegalArgumentException("질문 세트를 찾을 수 없습니다. topic=" + topic);
        }

        // ADMIN-02: 관리자가 문제 없는(또는 이 콤보에 필요한 타입이 빠진) 세트를 만들어도,
        // 그 세트가 무작위로 뽑히는 순간 출제가 실패하지 않도록 필요한 타입을 모두 가진
        // 세트 중에서만 무작위로 고른다. 같은 topic에 정상 세트가 하나라도 있으면 항상 그걸로 출제된다.
        List<QuestionSet> candidates = sets.stream()
                .filter(set -> hasAllTypes(set, pattern.questionTypes()))
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "이 조합에 필요한 문제 유형을 모두 가진 세트가 없습니다. topic=" + topic);
        }

        QuestionSet set = candidates.get(random.nextInt(candidates.size()));
        return pattern.questionTypes().stream()
                .map(type -> findQuestion(set, type))
                .map(QuestionDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuestionDto assembleSingle(SurveyTopic topic, QuestionType type) {
        List<QuestionSet> sets = setCache.computeIfAbsent(
                topic, questionSetRepository::findByTopicWithDetails);
        if (sets.isEmpty()) throw new IllegalArgumentException("topic=" + topic);

        // ADMIN-02: 위 assemble()과 동일한 이유 — 이 type을 가진 세트 중에서만 고른다.
        List<QuestionSet> candidates = sets.stream()
                .filter(set -> hasAllTypes(set, List.of(type)))
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("이 문제 유형을 가진 세트가 없습니다. topic=" + topic + ", type=" + type);
        }
        QuestionSet set = candidates.get(random.nextInt(candidates.size()));
        return QuestionDto.from(findQuestion(set, type));
    }

    // REVIEW-07: assemble()/assembleSingle()은 topic 안에서 "완전한 세트"만 후보로 거르지만(ADMIN-02),
    // 호출부가 여러 topic 중 하나를 무작위로 고르는 단계(PracticeTypeController 등)에서는 그 필터링이
    // 적용되지 않아 이 type을 낼 수 있는 세트가 하나도 없는 topic도 후보에 남아 있다가 뽑히면
    // assembleSingle()이 예외를 던지고, 다른 사용자 topic이 있어도 그냥 "연습 불가"로 끝났다.
    // 후보 topic 목록을 좁히는 단계에서 미리 걸러낼 수 있도록 공개 메서드로 노출한다.
    @Transactional(readOnly = true)
    public boolean hasQuestionType(SurveyTopic topic, QuestionType type) {
        List<QuestionSet> sets = setCache.computeIfAbsent(topic, questionSetRepository::findByTopicWithDetails);
        return sets.stream().anyMatch(set -> hasAllTypes(set, List.of(type)));
    }

    private boolean hasAllTypes(QuestionSet set, List<QuestionType> types) {
        Set<QuestionType> setTypes = set.getQuestions().stream()
                .map(Question::getQuestionType)
                .collect(Collectors.toSet());
        return setTypes.containsAll(types);
    }

    // 관리자 CRUD가 이 topic의 QuestionSet을 바꿨을 때 호출한다 (CACHE-01).
    // 캐시 소유자(QuestionAssemblyService) 밖에서 직접 Map을 건드리지 않도록 여기 하나로 배선을 모은다.
    public void evict(SurveyTopic topic) {
        setCache.remove(topic);
    }

    private Question findQuestion(QuestionSet set, QuestionType type) {
        return set.getQuestions().stream()
                .filter(question -> question.getQuestionType() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "질문 타입을 찾을 수 없습니다. set=" + set.getName() + ", type=" + type));
    }
}
