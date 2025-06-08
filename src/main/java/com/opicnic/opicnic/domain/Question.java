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

    // }
}