package com.opicnic.opicnic.domain.enums;

public enum SurveyDifficultyGroup {
    LEVEL_3_4("OPIc Levels 3-4"), // 사용자가 3 또는 4 선택 시
    LEVEL_5_6("OPIc Levels 5-6"); // 사용자가 5 또는 6 선택 시

    private final String label;

    SurveyDifficultyGroup(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
