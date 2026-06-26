package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.ExamSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExamScheduleRepository extends JpaRepository<ExamSchedule, Long> {
    Optional<ExamSchedule> findTopByMemberIdOrderByCreatedAtDesc(Long memberId);
}
