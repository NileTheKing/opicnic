package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.enums.SurveyTopic;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 온보딩과 마이페이지가 화면 문구로 약속하는 "12개 이상 + 그룹별 최소 개수"를
// 서버에서도 강제하는 단일 규칙 (PC-11). 브라우저 JS 검증은 사용자 편의일 뿐 source of truth가 아니다.
@Component
public class SurveyTopicPolicy {

    public static final int MIN_TOTAL_TOPICS = 12;

    private static final Map<String, List<SurveyTopic>> GROUPS = buildGroups();
    private static final Map<String, Integer> GROUP_MIN = Map.of(
            "여가 활동", 2,
            "취미 / 관심사", 1,
            "운동", 1,
            "여행 / 휴가", 1
    );
    private static final Set<SurveyTopic> ALLOWED_TOPICS = GROUPS.values().stream()
            .flatMap(List::stream)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private static Map<String, List<SurveyTopic>> buildGroups() {
        Map<String, List<SurveyTopic>> groups = new LinkedHashMap<>();
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
        return groups;
    }

    // 총 개수, 그룹별 최소, 허용되지 않은(거주/돌발) 주제 포함 여부를 모두 확인한다.
    public boolean isValid(List<SurveyTopic> topics) {
        if (topics == null) return false;
        if (topics.stream().anyMatch(t -> !ALLOWED_TOPICS.contains(t))) return false;

        Set<SurveyTopic> distinct = Set.copyOf(topics);
        if (distinct.size() < MIN_TOTAL_TOPICS) return false;

        for (var entry : GROUP_MIN.entrySet()) {
            long count = GROUPS.get(entry.getKey()).stream().filter(distinct::contains).count();
            if (count < entry.getValue()) return false;
        }
        return true;
    }
}
