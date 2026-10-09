package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    // questionSet(LAZY)까지 한 번에 읽는다 — QuestionDto.from()이 topic을 읽어도 트랜잭션·세션이 필요 없다
    @Query("select q from Question q join fetch q.questionSet where q.id = :id")
    Optional<Question> findWithQuestionSetById(@Param("id") Long id);
}
