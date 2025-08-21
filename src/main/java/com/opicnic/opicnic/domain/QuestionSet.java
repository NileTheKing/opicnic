package com.opicnic.opicnic.domain;


import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "question_set")
@SQLDelete(sql = "UPDATE question_set SET deleted = true WHERE id = ?")
@Where(clause = "deleted = false")
public class QuestionSet {

    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    @Id
    private Long id;

    private String name; // 예: "Movie Lover Set - Level 3"

    @Enumerated(EnumType.STRING)
    private SurveyDifficulty difficulty; // enum: LEVEL_1, LEVEL_2, LEVEL_3, LEVEL_4, LEVEL_5, LEVEL_6

    @Enumerated
    private SurveyTopic topic; // enum: MOVIE, MUSIC, BOOK, FOOD, TRAVEL, GAME
    //todo orphan? fetch?
    @OneToMany(mappedBy = "questionSet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequenceInSet ASC") // 세트 내 콤보 순서
    @BatchSize(size = 10) // N+1 문제 방지를 위한 배치 사이즈 설정
    private List<Combo> combos = new ArrayList<>(); // 질문 목록

    private boolean deleted = false;


    //생성자
    public QuestionSet(String name, SurveyDifficulty difficulty, SurveyTopic topic) {
        this.name = name;
        this.difficulty = difficulty;
        this.topic = topic;
    }

}