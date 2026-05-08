package com.opicnic.opicnic.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private String questionContent;

    @Column(columnDefinition = "TEXT")
    private String sttText;

    @Column(columnDefinition = "TEXT")
    private String vocabulary;

    @Column(columnDefinition = "TEXT")
    private String grammar;

    @Column(columnDefinition = "TEXT")
    private String mainPoint;

    @Column(columnDefinition = "TEXT")
    private String fluency;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String overall;

    @Column(columnDefinition = "TEXT")
    private String improvements;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
