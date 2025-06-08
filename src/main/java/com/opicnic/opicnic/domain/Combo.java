package com.opicnic.opicnic.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "combo")
public class Combo {

    @Id
    @GeneratedValue
    private Long id;

    private String name; // 예: "Recent Movie Experience"

    @ManyToOne
    @JoinColumn(name = "question_set_id")
    private QuestionSet questionSet; // 질문 세트

    private int sequenceInSet; // 세트 내 이 콤보의 순서

    @OneToMany(mappedBy = "combo", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequenceInCombo ASC") // 콤보 내 질문 순서
    @BatchSize(size = 3) // N+1 문제 방지를 위한 배치 사이즈 설정
    private List<Question> questions = new ArrayList<>(); // 질문 목록

    //편의용 생성자
    public Combo(String name, QuestionSet questionSet, int sequenceInSet) {
        this.name = name;
        this.questionSet = questionSet;
        this.sequenceInSet = sequenceInSet;
    }
}
