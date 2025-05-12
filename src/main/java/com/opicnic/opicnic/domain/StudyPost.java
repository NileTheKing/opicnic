package com.opicnic.opicnic.domain;

import com.opicnic.opicnic.domain.enums.Region;
import com.opicnic.opicnic.domain.enums.StudyStatus;
import com.opicnic.opicnic.domain.enums.StudyType;
import com.opicnic.opicnic.dto.StudyPostRequestDto;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class StudyPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Lob
    private String content;

    @Enumerated(EnumType.STRING)
    private StudyType studyType;

    @Enumerated(EnumType.STRING)
    @Setter
    private StudyStatus status = StudyStatus.RECRUITING;

    private int maxParticipants;

    @Setter
    private int currentParticipants;

    private String thumbnailUrl; // S3 URL

    @ElementCollection
    @CollectionTable(name = "study_post_regions", joinColumns = @JoinColumn(name = "post_id"))
    @Enumerated(EnumType.STRING)
    private List<Region> regions = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @Setter
    private Member writer;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    public void update(StudyPostRequestDto dto) {
        this.title = dto.getTitle();
        this.content = dto.getContent();
        this.maxParticipants = dto.getMaxParticipants();
        this.currentParticipants = dto.getCurrentParticipants();
        this.regions = dto.getRegions();
        this.studyType = dto.getStudyType();
    }

}