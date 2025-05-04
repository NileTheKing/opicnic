package com.opicnic.opicnic.domain.enums;

import lombok.Getter;

@Getter
public enum StudyType {
    PROJECT("프로젝트"),
    ALGORITHM("알고리즘"),
    TECHSTACK("기술 스택"),
    INTERVIEW("면접");

    private final String label;

    StudyType(String label) {
        this.label = label;
    }
}
