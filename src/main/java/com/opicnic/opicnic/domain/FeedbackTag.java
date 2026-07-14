package com.opicnic.opicnic.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_result_id", nullable = false)
    private FeedbackResult feedbackResult;

    // "mainPoint" | "vocab" | "sentence" | "imagery" | "accuracy" | "content"
    private String category;

    // e.g. "WHY_MISSING", "VOCAB_BASIC"
    private String tag;
}
