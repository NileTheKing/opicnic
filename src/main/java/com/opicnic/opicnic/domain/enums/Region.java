package com.opicnic.opicnic.domain.enums;

import lombok.Getter;

@Getter
public enum Region {
    GANGNAM("강남구"),
    GANGDONG("강동구"),
    GANGBUK("강북구"),
    GANGSEO("강서구"),
    MAPO("마포구"),
    JONGNO("종로구"),
    JUNG("중구"),
    YONGSAN("용산구"),
    SEONGDONG("성동구"),
    GWANGJIN("광진구"),
    DONGDAEMUN("동대문구"),
    JUNGNANG("중랑구"),
    SEONGBUK("성북구"),
    DOBONG("도봉구"),
    NOWON("노원구"),
    EUNPYEONG("은평구"),
    SEOCHO("서초구"),
    SONGPA("송파구"),
    GANGBUK2("강북구"),  // 중복이라면 제거 가능
    GEUMCHEON("금천구"),
    YEONGDEUNGPO("영등포구"),
    DONGJAK("동작구"),
    GWANAK("관악구"),
    GURU("구로구"),
    YANGCHEON("양천구");

    private final String label;

    Region(String label) {
        this.label = label;
    }
}