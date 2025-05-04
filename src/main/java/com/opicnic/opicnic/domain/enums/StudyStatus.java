package com.opicnic.opicnic.domain.enums;

import lombok.Getter;

@Getter
public enum StudyStatus {
    RECRUITING("모집중"),
    CLOSED("모집완료");

    private final String label;
    StudyStatus(String label) {
        this.label = label;
    }
}
