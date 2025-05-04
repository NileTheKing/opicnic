package com.opicnic.opicnic.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EnumResponse {
    private String code;   // Enum 의 이름 (ex. GANG NAM)
    private String label;  // 프론트에 보여줄 이름 (ex. 강남구)
}
