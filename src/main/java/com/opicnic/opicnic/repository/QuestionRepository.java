package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.Question;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    // QuestionRepository는 Question 엔티티에 대한 CRUD 작업을 수행하는 JpaRepository입니다.
    // 추가적인 메서드가 필요하다면 여기에 정의할 수 있습니다.
}
