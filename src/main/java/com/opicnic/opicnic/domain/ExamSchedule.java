package com.opicnic.opicnic.domain;

import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private LocalDate examDate;

    @Enumerated(EnumType.STRING)
    private SurveyProfile.TargetGrade targetGrade;

    private Integer dailyMinutes;

    private Integer studyDaysPerWeek;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
