package com.opicnic.opicnic.dto;


import com.opicnic.opicnic.domain.StudyPost;
import com.opicnic.opicnic.domain.enums.Region;
import com.opicnic.opicnic.domain.enums.StudyStatus;
import com.opicnic.opicnic.domain.enums.StudyType;
import lombok.Getter;

import java.time.format.DateTimeFormatter;
import java.util.List;
@Getter
public class StudyPostResponseDto {
    private Long id;
    private String title;
    private String content;
    private int maxParticipants;
    private int currentParticipants;
    private List<Region> regions;
    private StudyType studyType;
    private StudyStatus status;
    private String thumbnailUrl;
    private String writerName;
    private String createdAt;

    public static StudyPostResponseDto from(StudyPost post) {
        StudyPostResponseDto dto = new StudyPostResponseDto();
        dto.id = post.getId();
        dto.title = post.getTitle();
        dto.content = post.getContent();
        dto.maxParticipants = post.getMaxParticipants();
        dto.currentParticipants = post.getCurrentParticipants();
        dto.studyType = post.getStudyType();
        dto.status = post.getStatus();
        dto.regions = post.getRegions();
        dto.thumbnailUrl = post.getThumbnailUrl();
        dto.writerName = post.getWriter() != null ? post.getWriter().getNickname() : "(익명)";
        if (post.getCreatedAt() != null) {
            dto.createdAt = post.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return dto;
    }
}