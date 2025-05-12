package com.opicnic.opicnic.repository;


import com.opicnic.opicnic.domain.StudyPost;
import com.opicnic.opicnic.domain.enums.Region;
import com.opicnic.opicnic.domain.enums.StudyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StudyPostRepository extends JpaRepository<StudyPost, Long> {

    @Query("SELECT sp FROM StudyPost sp " +
            "WHERE (:regions IS NULL OR EXISTS (SELECT r FROM sp.regions r WHERE r IN :regions)) " +
            "AND (:studyTypes IS NULL OR sp.studyType IN :studyTypes)")
    List<StudyPost> findFiltered(List<Region> regions, List<StudyType> studyTypes);
}