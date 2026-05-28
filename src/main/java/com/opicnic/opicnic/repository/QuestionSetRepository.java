package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.QuestionSet;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface QuestionSetRepository extends JpaRepository<QuestionSet, Long> {

    List<QuestionSet> findByTopic(SurveyTopic topic);

    // questions만 fetch — combos는 @BatchSize로 lazy 로딩 (two-bag 동시 fetch 금지)
    @Query("SELECT DISTINCT qs FROM QuestionSet qs LEFT JOIN FETCH qs.questions WHERE qs.topic = :topic")
    List<QuestionSet> findByTopicWithDetails(@Param("topic") SurveyTopic topic);

    @Query("SELECT DISTINCT qs.topic FROM QuestionSet qs WHERE qs.topic IN :topics")
    List<SurveyTopic> findExistingTopics(@Param("topics") Collection<SurveyTopic> topics);
}
