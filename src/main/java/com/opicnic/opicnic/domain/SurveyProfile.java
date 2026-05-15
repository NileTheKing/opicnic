package com.opicnic.opicnic.domain;

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

    // 직업 상태 (학생/직장인/기타)
    @Enumerated(EnumType.STRING)
    private OccupationType occupationType;

    // 거주지 유형 (자택/기숙사/친구나 가족과 함께 등)
    @Enumerated(EnumType.STRING)
    private ResidenceType residenceType;

    // 선택한 주제 목록 (최대 12개, 콤마 구분 문자열로 저장)
    @ElementCollection
    @CollectionTable(name = "survey_profile_topics", joinColumns = @JoinColumn(name = "survey_profile_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "topic")
    @Builder.Default
    private List<SurveyTopic> selectedTopics = new ArrayList<>();

    public enum OccupationType {
        STUDENT("학생"),
        EMPLOYEE("직장인"),
        OTHER("기타");

        public final String label;
        OccupationType(String label) { this.label = label; }
    }

    public enum ResidenceType {
        WITH_FAMILY("가족과 함께"),
        ALONE("혼자"),
        WITH_FRIENDS("친구/룸메이트와 함께"),
        DORMITORY("기숙사");

        public final String label;
        ResidenceType(String label) { this.label = label; }
    }
}
