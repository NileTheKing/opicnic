package com.opicnic.opicnic.dto;


import com.opicnic.opicnic.domain.enums.Region;
import com.opicnic.opicnic.domain.enums.StudyStatus;
import com.opicnic.opicnic.domain.enums.StudyType;

import java.time.LocalDateTime;
import java.util.List;

public class StudyPostResponseDTO {
    private Long id;
    private String title;
    private String thumbnailUrl;
    private String nickname;
    private LocalDateTime createdAt;
    private StudyType studyType;
    private StudyStatus status;
    private List<Region> regions;
    private int currentMemberCount;
    private int maxMemberCount;
}
