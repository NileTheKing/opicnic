package com.opicnic.opicnic.dto;

import com.opicnic.opicnic.domain.StudyPost;
import com.opicnic.opicnic.domain.enums.Region;
import com.opicnic.opicnic.domain.enums.StudyType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class StudyPostRequestDto {
    private String title;
    private String content;
    private int maxParticipants;
    private int currentParticipants;
    private List<Region> regions;
    private StudyType studyType;

    public StudyPost toEntity() {
        return StudyPost.builder()
                .title(title)
                .content(content)
                .maxParticipants(maxParticipants)
                .currentParticipants(currentParticipants)
                .regions(regions)
                .studyType(studyType)
                .build();
    }
}