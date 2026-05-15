package com.opicnic.opicnic.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum QuestionType {
    TYPE_1("현재_묘사", "현재 시제 - 장소/종류 묘사"),
    TYPE_2("현재_루틴", "현재 시제 - 활동/루틴/단계 묘사"),
    TYPE_3("과거_최근경험", "과거 시제 - 최초 또는 최근 경험 설명"),
    TYPE_4("과거_인상경험", "과거 시제 - 인상적인 경험 설명"),
    TYPE_5("롤플레이_질문", "롤플레이 - 단순 질문하기"),
    TYPE_6("롤플레이_정보요청", "롤플레이 - 정보 요청"),
    TYPE_7("롤플레이_문제상황", "롤플레이 - 문제 상황 설명 및 대안 제시"),
    TYPE_8("과거_유사경험", "과거 시제 - 롤플레이 유형7과 유사한 경험 설명"),
    TYPE_9("비교대조", "과거/현재 - 인물/사물/기타 비교 및 대조"),
    TYPE_10("이슈설명", "사회적 이슈 또는 관심사 구체적 설명");

    private final String code;
    private final String description;
}
