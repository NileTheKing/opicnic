package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.StudyApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudyApplicationRepository extends JpaRepository<StudyApplication, Long> {
    boolean existsByPostIdAndApplicantId(Long postId, Long memberId);
    List<StudyApplication> findByPostId(Long postId);

}