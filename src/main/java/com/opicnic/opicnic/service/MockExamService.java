package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.SurveyProfile;
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MockExamService {

    private static final SurveyDifficulty DEFAULT_DIFFICULTY = SurveyDifficulty.LEVEL_3;
    private static final int SELECTED_TOPIC_COMBO_COUNT = 3;
    private static final int SURPRISE_TOPIC_COMBO_COUNT = 2;

    private final OpicComboPatternProvider comboPatternProvider;
    private final QuestionAssemblyService questionAssemblyService;
    private final QuestionSetRepository questionSetRepository;
    private final TopicCatalog topicCatalog;
    private final Random random;

    public List<QuestionDto> createMockExam(SurveyProfile profile) {
        SurveyDifficulty difficulty = profile.getPreferredDifficulty() != null
                ? profile.getPreferredDifficulty()
                : DEFAULT_DIFFICULTY;
        List<ComboPattern> patterns = comboPatternProvider.getPatterns(difficulty);
        List<Integer> surpriseSlots = pickSurpriseSlots(patterns.size());
        List<SurveyTopic> availableTopics = findAvailablePracticeTopics();

        List<SurveyTopic> selectedTopics = pickSelectedTopics(profile.getSelectedTopics(), availableTopics);
        List<SurveyTopic> surpriseTopics = pickSurpriseTopics(profile.getSelectedTopics(), selectedTopics, availableTopics);

        List<QuestionDto> questions = new ArrayList<>();
        questions.add(selfIntroductionQuestion());

        int selectedIndex = 0;
        int surpriseIndex = 0;
        for (int i = 0; i < patterns.size(); i++) {
            SurveyTopic topic = surpriseSlots.contains(i)
                    ? surpriseTopics.get(surpriseIndex++)
                    : selectedTopics.get(selectedIndex++);
            questions.addAll(questionAssemblyService.assemble(topic, patterns.get(i)));
        }

        return questions;
    }

    private List<SurveyTopic> findAvailablePracticeTopics() {
        List<SurveyTopic> practiceTopics = topicCatalog.practiceTopics();
        List<SurveyTopic> existingTopics = questionSetRepository.findExistingTopics(practiceTopics);
        List<SurveyTopic> availableTopics = practiceTopics.stream()
                .filter(existingTopics::contains)
                .toList();
        if (availableTopics.isEmpty()) {
            throw new IllegalStateException("모의고사에 사용할 수 있는 질문 세트가 없습니다.");
        }
        return availableTopics;
    }

    private List<Integer> pickSurpriseSlots(int patternSize) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < patternSize; i++) {
            slots.add(i);
        }
        Collections.shuffle(slots, random);
        return slots.stream()
                .limit(SURPRISE_TOPIC_COMBO_COUNT)
                .sorted()
                .toList();
    }

    private List<SurveyTopic> pickSelectedTopics(List<SurveyTopic> profileTopics, List<SurveyTopic> availableTopics) {
        List<SurveyTopic> candidates = profileTopics.stream()
                .filter(topic -> topic != SurveyTopic.NO_EXERCISE)
                .filter(availableTopics::contains)
                .toList();

        List<SurveyTopic> distinctGroups = pickOnePerGroup(candidates, SELECTED_TOPIC_COMBO_COUNT);
        if (distinctGroups.size() >= SELECTED_TOPIC_COMBO_COUNT) {
            Collections.shuffle(distinctGroups, random);
            return distinctGroups.subList(0, SELECTED_TOPIC_COMBO_COUNT);
        }

        LinkedHashSet<SurveyTopic> fallback = new LinkedHashSet<>(distinctGroups);
        List<SurveyTopic> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, random);
        fallback.addAll(shuffled);
        fallback.addAll(availableTopics);

        List<SurveyTopic> fallbackTopics = fallback.stream().toList();
        if (fallbackTopics.size() < SELECTED_TOPIC_COMBO_COUNT) {
            throw new IllegalStateException("모의고사 선택 주제 후보가 부족합니다.");
        }
        return fallbackTopics.stream()
                .limit(SELECTED_TOPIC_COMBO_COUNT)
                .toList();
    }

    private List<SurveyTopic> pickOnePerGroup(List<SurveyTopic> topics, int limit) {
        List<SurveyTopic> shuffled = new ArrayList<>(topics);
        Collections.shuffle(shuffled, random);

        Set<String> usedGroups = new LinkedHashSet<>();
        List<SurveyTopic> picked = new ArrayList<>();
        for (SurveyTopic topic : shuffled) {
            String group = topicCatalog.groupOf(topic);
            if (usedGroups.add(group)) {
                picked.add(topic);
            }
            if (picked.size() == limit) {
                break;
            }
        }
        return picked;
    }

    private List<SurveyTopic> pickSurpriseTopics(List<SurveyTopic> profileTopics,
                                                List<SurveyTopic> selectedTopics,
                                                List<SurveyTopic> availableTopics) {
        Set<SurveyTopic> excluded = new LinkedHashSet<>(profileTopics);
        excluded.addAll(selectedTopics);
        excluded.add(SurveyTopic.NO_EXERCISE);

        // TODO: 별도 돌발 주제 문제은행이 생기면 supported topic 대체 후보가 아니라
        // 서베이 밖 돌발 주제 pool에서만 선택하도록 분리한다.
        List<SurveyTopic> candidates = availableTopics.stream()
                .filter(topic -> !excluded.contains(topic))
                .toList();
        if (candidates.size() < SURPRISE_TOPIC_COMBO_COUNT) {
            throw new IllegalStateException("모의고사 돌발 주제 후보가 부족합니다.");
        }

        List<SurveyTopic> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, random);
        return shuffled.stream()
                .limit(SURPRISE_TOPIC_COMBO_COUNT)
                .toList();
    }

    private QuestionDto selfIntroductionQuestion() {
        return new QuestionDto(
                null,
                "Please introduce yourself. Tell me about who you are, what you do, and anything important about yourself.",
                "자기소개",
                null
        );
    }

}
