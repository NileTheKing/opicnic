package com.opicnic.opicnic.domain;

import com.opicnic.opicnic.domain.enums.Region;
import com.opicnic.opicnic.domain.enums.StudyStatus;
import com.opicnic.opicnic.domain.enums.StudyType;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class StudyPost {

    @Id
    @GeneratedValue
    private Long id;

    private String title;

    @Lob
    private String content;

    @Enumerated(EnumType.STRING)
    private StudyType studyType;

    @Enumerated(EnumType.STRING)
    private StudyStatus status = StudyStatus.RECRUITING;

    private int maxParticipants;

    private String thumbnailUrl; // S3 URL

    @ElementCollection
    @CollectionTable(name = "study_post_regions", joinColumns = @JoinColumn(name = "post_id"))
    @Enumerated(EnumType.STRING)
    private List<Region> regions = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    private Member writer;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();
}