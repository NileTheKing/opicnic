package com.opicnic.opicnic.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "question") // 테이블명 명시
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT") // 긴 질문 내용을 위해
    private String content; // 'text'에서 'content'로 변경 (의미 명확화)

    private int sequenceInCombo; // 콤보 내 이 문제의 순서

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id")
    private Combo combo;

    // 편의 생성자
    public Question(String content, int sequenceInCombo, Combo combo) {
        this.content = content;
        this.sequenceInCombo = sequenceInCombo;
        this.combo = combo;
    }

    // 기존 FixedComboQuestionStrategy와의 호환성을 위해 임시로 남겨둘 수 있었던 필드들.
    // 하지만 새로운 구조에서는 Combo와 QuestionSet에서 주제/난이도를 가져오는 것이 이상적입니다.
    // 이 예제에서는 위 편의 생성자를 사용합니다.
    // public Question(Long id, String content, String topic, String difficulty) {
    //    this.id = id;
    //    this.content = content;
    //    // this.topic = topic; // 더 이상 Question 엔티티에 직접 저장하지 않음
    //    // this.difficulty = difficulty; // 더 이상 Question 엔티티에 직접 저장하지 않음
    // }
}