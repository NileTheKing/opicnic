package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.QuestionSet;
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionSetRepository extends JpaRepository<QuestionSet, Long> {

    // 특정 주제와 난이도에 맞는 세트 목록 검색 (콤보와 질문들을 함께 페치)
    // N+1 문제를 피하기 위해 JOIN FETCH 사용
    @Query("SELECT DISTINCT qs FROM QuestionSet qs LEFT JOIN FETCH qs.combos c LEFT JOIN FETCH c.questions WHERE qs.topic = :topic AND qs.difficulty = :difficulty")
    List<QuestionSet> findByTopicAndDifficultyWithDetails(@Param("topic") SurveyTopic topic, @Param("difficulty") SurveyDifficulty difficulty);

    // 기본 JpaRepository 메서드 외에 필요에 따라 추가 가능
    // 예: List<QuestionSet> findByTopic(SurveyTopic topic);
    // 예: List<QuestionSet> findByDifficulty(SurveyDifficulty difficulty);
}