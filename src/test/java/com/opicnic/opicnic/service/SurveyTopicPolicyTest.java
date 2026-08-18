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
}
