package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.enums.SurveyTopic;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

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
            Map.entry(SurveyTopic.LIVING_ALONE, "fa-person-shelter"),
            Map.entry(SurveyTopic.MOVIE_WATCHING, "fa-film"),
            Map.entry(SurveyTopic.TV_WATCHING, "fa-tv"),
            Map.entry(SurveyTopic.PERFORMANCE_WATCHING, "fa-masks-theater"),
            Map.entry(SurveyTopic.CONCERT_WATCHING, "fa-music"),
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
            Map.entry(SurveyTopic.DOMESTIC_TRAVEL, "fa-map-location-dot"),
            Map.entry(SurveyTopic.INTERNATIONAL_TRAVEL, "fa-plane"),
            // 돌발 주제 아이콘
            Map.entry(SurveyTopic.BANK_VISIT, "fa-building-columns"),
            Map.entry(SurveyTopic.LIBRARY_VISIT, "fa-book"),
            Map.entry(SurveyTopic.HOTEL_STAY, "fa-hotel"),
            Map.entry(SurveyTopic.RESTAURANT_VISIT, "fa-utensils"),
            Map.entry(SurveyTopic.PUBLIC_TRANSPORTATION, "fa-bus"),
            Map.entry(SurveyTopic.WEATHER, "fa-cloud-sun"),
            Map.entry(SurveyTopic.HOLIDAY_FESTIVAL, "fa-champagne-glasses"),
            Map.entry(SurveyTopic.FASHION, "fa-shirt"),
            Map.entry(SurveyTopic.NEIGHBORHOOD, "fa-people-group"),
            Map.entry(SurveyTopic.TECHNOLOGY_INTERNET, "fa-wifi"),
            Map.entry(SurveyTopic.MOBILE_PHONE, "fa-mobile-screen"),
            Map.entry(SurveyTopic.FOOD, "fa-bowl-food"),
            Map.entry(SurveyTopic.FURNITURE, "fa-couch"),
            Map.entry(SurveyTopic.GEOGRAPHY, "fa-mountain"),
            Map.entry(SurveyTopic.APPOINTMENT, "fa-calendar-check"),
            Map.entry(SurveyTopic.PARTY, "fa-cake-candles"),
            Map.entry(SurveyTopic.DIET, "fa-scale-balanced"),
            Map.entry(SurveyTopic.HOME_APPLIANCE, "fa-blender"),
            Map.entry(SurveyTopic.LEISURE_GENERAL, "fa-face-smile"),
            Map.entry(SurveyTopic.TECHNOLOGY, "fa-microchip"),
            Map.entry(SurveyTopic.HEALTH_WELLNESS, "fa-heart-pulse"),
            Map.entry(SurveyTopic.INDUSTRY, "fa-industry"),
            Map.entry(SurveyTopic.RECYCLING, "fa-recycle")
    );

    private static final Map<String, List<SurveyTopic>> SURPRISE_TOPIC_GROUPS = buildSurpriseTopicGroups();

    private static final List<SurveyTopic> SURPRISE_TOPICS_LIST = SURPRISE_TOPIC_GROUPS.values().stream()
            .flatMap(List::stream)
            .toList();

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

    public List<SurveyTopic> surpriseTopics() {
        return SURPRISE_TOPICS_LIST;
    }

    public Map<String, List<SurveyTopic>> surpriseTopicGroups() {
        return new LinkedHashMap<>(SURPRISE_TOPIC_GROUPS);
    }

    private static Map<String, List<SurveyTopic>> buildSurpriseTopicGroups() {
        Map<String, List<SurveyTopic>> groups = new LinkedHashMap<>();
        groups.put("장소 / 서비스", List.of(
                SurveyTopic.BANK_VISIT, SurveyTopic.RESTAURANT_VISIT,
                SurveyTopic.HOTEL_STAY, SurveyTopic.LIBRARY_VISIT
        ));
        groups.put("생활용품", List.of(
                SurveyTopic.FURNITURE, SurveyTopic.HOME_APPLIANCE,
                SurveyTopic.MOBILE_PHONE, SurveyTopic.FASHION
        ));
        groups.put("사회 / 기술", List.of(
                SurveyTopic.PUBLIC_TRANSPORTATION, SurveyTopic.TECHNOLOGY_INTERNET,
                SurveyTopic.TECHNOLOGY, SurveyTopic.INDUSTRY,
                SurveyTopic.RECYCLING, SurveyTopic.GEOGRAPHY
        ));
        groups.put("일상 / 문화", List.of(
                SurveyTopic.HOLIDAY_FESTIVAL, SurveyTopic.PARTY,
                SurveyTopic.APPOINTMENT, SurveyTopic.LEISURE_GENERAL,
                SurveyTopic.NEIGHBORHOOD
        ));
        groups.put("건강 / 음식", List.of(
                SurveyTopic.WEATHER, SurveyTopic.FOOD,
                SurveyTopic.HEALTH_WELLNESS, SurveyTopic.DIET
        ));
        return Collections.unmodifiableMap(groups);
    }

    private static Map<SurveyTopic, String> buildGroupByTopic() {
        Map<SurveyTopic, String> groups = new EnumMap<>(SurveyTopic.class);
        TOPIC_GROUPS.forEach((group, topics) -> topics.forEach(topic -> groups.put(topic, group)));
        return Map.copyOf(groups);
    }

    private static Map<String, List<SurveyTopic>> buildTopicGroups() {
        Map<String, List<SurveyTopic>> groups = new LinkedHashMap<>();
        groups.put("거주 형태", List.of(SurveyTopic.LIVING_WITH_FAMILY, SurveyTopic.LIVING_ALONE));
        groups.put("여가 활동", List.of(
                SurveyTopic.MOVIE_WATCHING, SurveyTopic.TV_WATCHING, SurveyTopic.PERFORMANCE_WATCHING,
                SurveyTopic.CONCERT_WATCHING, SurveyTopic.PARK_GOING, SurveyTopic.BEACH_GOING,
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
                SurveyTopic.STAYCATION, SurveyTopic.DOMESTIC_TRAVEL, SurveyTopic.INTERNATIONAL_TRAVEL
        ));
        return Collections.unmodifiableMap(groups);
    }
}
