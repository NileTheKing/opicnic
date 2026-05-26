package com.opicnic.opicnic.domain;

import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    @Enumerated(EnumType.STRING)
    private OccupationType occupationType;

    private boolean isStudent;

    @Enumerated(EnumType.STRING)
    private ResidenceType residenceType;

    @Enumerated(EnumType.STRING)
    private TargetGrade targetGrade;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SurveyDifficulty preferredDifficulty = SurveyDifficulty.LEVEL_4;

    @ElementCollection
    @CollectionTable(name = "survey_profile_topics", joinColumns = @JoinColumn(name = "survey_profile_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "topic")
    @Builder.Default
    private List<SurveyTopic> selectedTopics = new ArrayList<>();

    public enum OccupationType {
        NO_WORK_EXPERIENCE("일 경험 없음"),
        BUSINESS("사업/회사"),
        REMOTE_WORK("재택근무/재택사업"),
        TEACHER("교사/교육자");

        public final String label;
        OccupationType(String label) { this.label = label; }
    }

    public enum TargetGrade {
        NH("NH",   SurveyDifficulty.LEVEL_3, 3, 3),
        IL("IL",   SurveyDifficulty.LEVEL_3, 3, 3),
        IM1("IM1", SurveyDifficulty.LEVEL_4, 4, 4),
        IM2("IM2", SurveyDifficulty.LEVEL_4, 4, 4),
        IM3("IM3", SurveyDifficulty.LEVEL_5, 5, 5),
        IH("IH",   SurveyDifficulty.LEVEL_5, 5, 5),
        AL("AL",   SurveyDifficulty.LEVEL_5, 5, 6);

        public final String label;
        public final SurveyDifficulty recommendedDifficulty;
        public final int minLevel;
        public final int maxLevel;

        TargetGrade(String label, SurveyDifficulty recommended, int min, int max) {
            this.label = label;
            this.recommendedDifficulty = recommended;
            this.minLevel = min;
            this.maxLevel = max;
        }
    }

    public enum ResidenceType {
        WITH_FAMILY("가족과 함께"),
        WITH_FRIENDS("친구/룸메이트와 함께"),
        DORMITORY("기숙사");

        public final String label;
        ResidenceType(String label) { this.label = label; }
    }
}
