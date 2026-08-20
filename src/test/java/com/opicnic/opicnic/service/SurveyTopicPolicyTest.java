package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.enums.SurveyTopic;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SurveyTopicPolicyTest {

    private static final List<SurveyTopic> VALID_TOPICS = List.of(
            SurveyTopic.MOVIE_WATCHING, SurveyTopic.TV_WATCHING,
            SurveyTopic.MUSIC_LISTENING,
            SurveyTopic.NO_EXERCISE,
            SurveyTopic.STAYCATION,
            SurveyTopic.READING, SurveyTopic.SINGING, SurveyTopic.COOKING,
            SurveyTopic.WALKING, SurveyTopic.JOGGING,
            SurveyTopic.DOMESTIC_TRAVEL, SurveyTopic.INTERNATIONAL_TRAVEL
    );

    @Test
    void validSetPassesTotalAndGroupMinimums() {
        SurveyTopicPolicy policy = new SurveyTopicPolicy();
        assertThat(policy.isValid(VALID_TOPICS)).isTrue();
    }

    @Test
    void fewerThanTwelveIsRejected() {
        SurveyTopicPolicy policy = new SurveyTopicPolicy();
        List<SurveyTopic> eleven = new ArrayList<>(VALID_TOPICS);
        eleven.remove(0);
        assertThat(policy.isValid(eleven)).isFalse();
    }

    @Test
    void missingGroupMinimumIsRejectedEvenWithTwelveTotal() {
        SurveyTopicPolicy policy = new SurveyTopicPolicy();
        // 여가 활동 그룹(최소 2)에서 하나를 빼고 취미 그룹에서 하나를 더해 총 12개는 유지
        List<SurveyTopic> broken = new ArrayList<>(VALID_TOPICS);
        broken.remove(SurveyTopic.TV_WATCHING);
        broken.add(SurveyTopic.INSTRUMENT_PLAYING);
        assertThat(policy.isValid(broken)).isFalse();
    }

    @Test
    void nullOrDisallowedTopicIsRejected() {
        SurveyTopicPolicy policy = new SurveyTopicPolicy();
        assertThat(policy.isValid(null)).isFalse();

        List<SurveyTopic> withResidence = new ArrayList<>(VALID_TOPICS);
        withResidence.add(SurveyTopic.LIVING_WITH_FAMILY);
        assertThat(policy.isValid(withResidence)).isFalse();
    }

    // REVIEW-06 회귀 테스트: distinct 개수만 보면 [JOGGING, JOGGING, ...]처럼 중복 제출도
    // "실질적으로 12개 이상"이면 통과해버렸다 — 그 결과 원본(중복 포함) 리스트가 그대로
    // profile.selectedTopics(List, Set 아님)에 저장될 수 있었다. 제출 리스트 자체에 중복이
    // 있으면 총 개수·그룹 조건과 무관하게 거부해야 한다.
    @Test
    void duplicateTopicInSubmittedListIsRejectedEvenWithEnoughDistinctTopics() {
        SurveyTopicPolicy policy = new SurveyTopicPolicy();
        List<SurveyTopic> withDuplicate = new ArrayList<>(VALID_TOPICS);
        withDuplicate.add(VALID_TOPICS.get(0)); // 이미 있는 주제를 한 번 더 추가 (distinct는 여전히 12개)

        assertThat(policy.isValid(withDuplicate)).isFalse();
    }

    @Test
    void isAllowedTopicRejectsSurpriseAndResidenceTopics() {
        SurveyTopicPolicy policy = new SurveyTopicPolicy();
        assertThat(policy.isAllowedTopic(SurveyTopic.SINGING)).isTrue();
        assertThat(policy.isAllowedTopic(SurveyTopic.NO_EXERCISE)).isTrue();
        assertThat(policy.isAllowedTopic(SurveyTopic.BANK_VISIT)).isFalse(); // 돌발 전용
        assertThat(policy.isAllowedTopic(SurveyTopic.LIVING_WITH_FAMILY)).isFalse(); // 거주 전용
        assertThat(policy.isAllowedTopic(null)).isFalse();
    }
}
