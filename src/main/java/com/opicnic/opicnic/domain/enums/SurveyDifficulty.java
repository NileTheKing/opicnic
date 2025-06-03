// src/main/java/com/opicnic.opicnic.domain.enums/SurveyDifficulty.java
package com.opicnic.opicnic.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SurveyDifficulty {
    LEVEL_1("레벨1"),
    LEVEL_2("레벨2"), // IM2를 포함하는 예시
    LEVEL_3("레벨3"),  // IH를 포함하는 예시
    LEVEL_4("레벨4"), // AL을 포함하는 예시
    LEVEL_5("레벨5"),
    LEVEL_6("레벨6");

    private final String label; // 화면에 보여줄 이름

    // toString() 오버라이드하여 필요시 enum.name() 대신 label 반환 가능
    @Override
    public String toString() {
        return this.label;
    }
}