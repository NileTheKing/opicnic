package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.SurveyProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SurveyProfileRepository extends JpaRepository<SurveyProfile, Long> {
    Optional<SurveyProfile> findByMemberId(Long memberId);
}
