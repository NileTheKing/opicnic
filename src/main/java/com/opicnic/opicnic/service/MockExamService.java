package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.SurveyProfile;
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.dto.QuestionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MockExamService {

    private static final SurveyDifficulty DEFAULT_DIFFICULTY = SurveyDifficulty.LEVEL_3;
    private static final int SELECTED_TOPIC_COMBO_COUNT = 3;
    private static final int SURPRISE_TOPIC_COMBO_COUNT = 2;

    private static final List<SurveyTopic> SUPPORTED_TOPICS = List.of(
            SurveyTopic.LIVING_WITH_FAMILY,
            SurveyTopic.MOVIE_WATCHING, SurveyTopic.TV_WATCHING, SurveyTopic.PERFORMANCE_WATCHING,
            SurveyTopic.PARK_GOING, SurveyTopic.BEACH_GOING, SurveyTopic.SPORTS_WATCHING,
            SurveyTopic.COFFEE_SHOP_GOING, SurveyTopic.SHOPPING,
            SurveyTopic.MUSIC_LISTENING, SurveyTopic.INSTRUMENT_PLAYING,
            SurveyTopic.SINGING, SurveyTopic.COOKING, SurveyTopic.READING,
            SurveyTopic.NO_EXERCISE, SurveyTopic.WALKING, SurveyTopic.JOGGING, SurveyTopic.FITNESS_GYM,
            SurveyTopic.STAYCATION, SurveyTopic.DOMESTIC_TRAVEL
    );

    private static final Map<SurveyTopic, String> TOPIC_GROUPS = new EnumMap<>(SurveyTopic.class);

    static {
        putGroup("거주 형태", SurveyTopic.LIVING_WITH_FAMILY);
        putGroup("여가 활동",
                SurveyTopic.MOVIE_WATCHING, SurveyTopic.TV_WATCHING, SurveyTopic.PERFORMANCE_WATCHING,
                SurveyTopic.PARK_GOING, SurveyTopic.BEACH_GOING, SurveyTopic.SPORTS_WATCHING,
                SurveyTopic.COFFEE_SHOP_GOING, SurveyTopic.SHOPPING);
        putGroup("취미 / 관심사",
                SurveyTopic.MUSIC_LISTENING, SurveyTopic.INSTRUMENT_PLAYING,
                SurveyTopic.SINGING, SurveyTopic.COOKING, SurveyTopic.READING);
        putGroup("운동",
                SurveyTopic.NO_EXERCISE, SurveyTopic.WALKING, SurveyTopic.JOGGING, SurveyTopic.FITNESS_GYM);
        putGroup("여행 / 휴가", SurveyTopic.STAYCATION, SurveyTopic.DOMESTIC_TRAVEL);
    }

    private final OpicComboPatternProvider comboPatternProvider;
    private final QuestionAssemblyService questionAssemblyService;
    private final Random random = new Random();

    public List<QuestionDto> createMockExam(SurveyProfile profile) {
        SurveyDifficulty difficulty = profile.getPreferredDifficulty() != null
                ? profile.getPreferredDifficulty()
                : DEFAULT_DIFFICULTY;
        List<ComboPattern> patterns = comboPatternProvider.getPatterns(difficulty);
        List<Integer> surpriseSlots = pickSurpriseSlots(patterns.size());

        List<SurveyTopic> selectedTopics = pickSelectedTopics(profile.getSelectedTopics());
        List<SurveyTopic> surpriseTopics = pickSurpriseTopics(profile.getSelectedTopics(), selectedTopics);

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

    private List<SurveyTopic> pickSelectedTopics(List<SurveyTopic> profileTopics) {
        List<SurveyTopic> candidates = profileTopics.stream()
                .filter(topic -> topic != SurveyTopic.NO_EXERCISE)
                .filter(SUPPORTED_TOPICS::contains)
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
        fallback.addAll(SUPPORTED_TOPICS.stream()
                .filter(topic -> topic != SurveyTopic.NO_EXERCISE)
                .toList());

        return fallback.stream()
                .limit(SELECTED_TOPIC_COMBO_COUNT)
                .toList();
    }

    private List<SurveyTopic> pickOnePerGroup(List<SurveyTopic> topics, int limit) {
        List<SurveyTopic> shuffled = new ArrayList<>(topics);
        Collections.shuffle(shuffled, random);

        Set<String> usedGroups = new LinkedHashSet<>();
        List<SurveyTopic> picked = new ArrayList<>();
        for (SurveyTopic topic : shuffled) {
            String group = TOPIC_GROUPS.getOrDefault(topic, topic.name());
            if (usedGroups.add(group)) {
                picked.add(topic);
            }
            if (picked.size() == limit) {
                break;
            }
        }
        return picked;
    }

    private List<SurveyTopic> pickSurpriseTopics(List<SurveyTopic> profileTopics, List<SurveyTopic> selectedTopics) {
        Set<SurveyTopic> excluded = new LinkedHashSet<>(profileTopics);
        excluded.addAll(selectedTopics);
        excluded.add(SurveyTopic.NO_EXERCISE);

        // TODO: 별도 돌발 주제 문제은행이 생기면 SURPORTED_TOPICS 대체 후보가 아니라
        // 서베이 밖 돌발 주제 pool에서만 선택하도록 분리한다.
        List<SurveyTopic> candidates = SUPPORTED_TOPICS.stream()
                .filter(topic -> !excluded.contains(topic))
                .toList();
        if (candidates.size() < SURPRISE_TOPIC_COMBO_COUNT) {
            candidates = SUPPORTED_TOPICS.stream()
                    .filter(topic -> topic != SurveyTopic.NO_EXERCISE)
                    .filter(topic -> !selectedTopics.contains(topic))
                    .toList();
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

    private static void putGroup(String group, SurveyTopic... topics) {
        for (SurveyTopic topic : topics) {
            TOPIC_GROUPS.put(topic, group);
        }
    }
}
