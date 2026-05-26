package com.opicnic.opicnic.domain;

import com.opicnic.opicnic.domain.enums.QuestionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "combo")
public class Combo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_set_id")
    private QuestionSet questionSet;

    @ElementCollection
    @CollectionTable(name = "combo_question_types", joinColumns = @JoinColumn(name = "combo_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "question_type")
    @OrderColumn(name = "position")
    private List<QuestionType> questionTypes = new ArrayList<>();

    public Combo(String name, QuestionSet questionSet, List<QuestionType> questionTypes) {
        this.name = name;
        this.questionSet = questionSet;
        this.questionTypes = new ArrayList<>(questionTypes);
    }
}
