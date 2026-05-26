package com.opicnic.opicnic.domain;

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

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private SurveyTopic topic;

    @OneToMany(mappedBy = "questionSet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 10)
    private List<Question> questions = new ArrayList<>();

    @OneToMany(mappedBy = "questionSet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 5)
    private List<Combo> combos = new ArrayList<>();

    private boolean deleted = false;

    public QuestionSet(String name, SurveyTopic topic) {
        this.name = name;
        this.topic = topic;
    }
}
