package com.opicnic.opicnic.dto;

import com.opicnic.opicnic.domain.StudyApplication;

public record StudyApplicationResponseDto(
        Long id,
        String applicantNickname,
        String status,
        String appliedAt
) {
    public static StudyApplicationResponseDto from(StudyApplication entity) {
        return new StudyApplicationResponseDto(
                entity.getId(),
                entity.getApplicant().getNickname(),
                entity.getStatus().name(),
                entity.getAppliedAt()
        );
    }
}