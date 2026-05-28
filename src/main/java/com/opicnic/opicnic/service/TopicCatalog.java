package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.enums.SurveyTopic;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("java:S1192")

@Component
public class TopicCatalog {

    private static final Map<String, List<SurveyTopic>> TOPIC_GROUPS = buildTopicGroups();

    private static final List<SurveyTopic> SUPPORTED_TOPICS = TOPIC_GROUPS.values().stream()
            .flatMap(List::stream)
            .toList();

    private static final Map<SurveyTopic, String> GROUP_BY_TOPIC = buildGroupByTopic();

    private static final Set<SurveyTopic> RECOMMENDED_TOPICS = Set.copyOf(SUPPORTED_TOPICS);

    private static final Map<SurveyTopic, String> TOPIC_ICONS = Map.ofEntries(
            Map.entry(SurveyTopic.LIVING_WITH_FAMILY, "fa-people-roof"),
            Map.entry(SurveyTopic.MOVIE_WATCHING, "fa-film"),
            Map.entry(SurveyTopic.TV_WATCHING, "fa-tv"),
            Map.entry(SurveyTopic.PERFORMANCE_WATCHING, "fa-masks-theater"),
            Map.entry(SurveyTopic.PARK_GOING, "fa-tree"),
            Map.entry(SurveyTopic.BEACH_GOING, "fa-umbrella-beach"),
            Map.entry(SurveyTopic.SPORTS_WATCHING, "fa-futbol"),
            Map.entry(SurveyTopic.COFFEE_SHOP_GOING, "fa-mug-hot"),
            Map.entry(SurveyTopic.SHOPPING, "fa-bag-shopping"),
            Map.entry(SurveyTopic.MUSIC_LISTENING, "fa-headphones"),
            Map.entry(SurveyTopic.INSTRUMENT_PLAYING, "fa-guitar"),
            Map.entry(SurveyTopic.READING, "fa-book-open"),
            Map.entry(SurveyTopic.SINGING, "fa-microphone"),
            Map.entry(SurveyTopic.COOKING, "fa-utensils"),
            Map.entry(SurveyTopic.NO_EXERCISE, "fa-couch"),
            Map.entry(SurveyTopic.WALKING, "fa-person-walking"),
            Map.entry(SurveyTopic.JOGGING, "fa-person-running"),
            Map.entry(SurveyTopic.FITNESS_GYM, "fa-dumbbell"),
            Map.entry(SurveyTopic.STAYCATION, "fa-house"),
            Map.entry(SurveyTopic.DOMESTIC_TRAVEL, "fa-map-location-dot")
    );

    public List<SurveyTopic> supportedTopics() {
        return SUPPORTED_TOPICS;
    }

    public List<SurveyTopic> practiceTopics() {
        return SUPPORTED_TOPICS.stream()
                .filter(topic -> topic != SurveyTopic.NO_EXERCISE)
                .toList();
    }

    public Map<String, List<SurveyTopic>> groupedTopics() {
        return new LinkedHashMap<>(TOPIC_GROUPS);
    }

    public Set<SurveyTopic> recommendedTopics() {
        return RECOMMENDED_TOPICS;
    }

    public Map<SurveyTopic, String> topicIcons() {
        return TOPIC_ICONS;
    }

    public String groupOf(SurveyTopic topic) {
        return GROUP_BY_TOPIC.getOrDefault(topic, topic.name());
    }

    private static Map<SurveyTopic, String> buildGroupByTopic() {
        Map<SurveyTopic, String> groups = new EnumMap<>(SurveyTopic.class);
        TOPIC_GROUPS.forEach((group, topics) -> topics.forEach(topic -> groups.put(topic, group)));
        return Map.copyOf(groups);
    }

    private static Map<String, List<SurveyTopic>> buildTopicGroups() {
        Map<String, List<SurveyTopic>> groups = new LinkedHashMap<>();
        groups.put("거주 형태", List.of(SurveyTopic.LIVING_WITH_FAMILY));
        groups.put("여가 활동", List.of(
                SurveyTopic.MOVIE_WATCHING, SurveyTopic.TV_WATCHING, SurveyTopic.PERFORMANCE_WATCHING,
                SurveyTopic.PARK_GOING, SurveyTopic.BEACH_GOING,
                SurveyTopic.SPORTS_WATCHING, SurveyTopic.COFFEE_SHOP_GOING, SurveyTopic.SHOPPING
        ));
        groups.put("취미 / 관심사", List.of(
                SurveyTopic.MUSIC_LISTENING, SurveyTopic.INSTRUMENT_PLAYING,
                SurveyTopic.READING, SurveyTopic.SINGING, SurveyTopic.COOKING
        ));
        groups.put("운동", List.of(
                SurveyTopic.NO_EXERCISE, SurveyTopic.WALKING, SurveyTopic.JOGGING, SurveyTopic.FITNESS_GYM
        ));
        groups.put("여행 / 휴가", List.of(
                SurveyTopic.STAYCATION, SurveyTopic.DOMESTIC_TRAVEL
        ));
        return Collections.unmodifiableMap(groups);
    }
}
