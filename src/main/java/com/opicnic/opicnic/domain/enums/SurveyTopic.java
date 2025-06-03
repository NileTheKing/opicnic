package com.opicnic.opicnic.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SurveyTopic {
    // --- 1. 여가 활동 (Leisure Activities) ---
    // 미디어 관련
    MOVIE_WATCHING("영화 보기"),
    MUSIC_LISTENING("음악 감상하기"),
    TV_WATCHING("TV 시청하기"),
    RADIO_LISTENING("라디오 청취하기"),
    PERFORMANCE_WATCHING("공연 보기 (콘서트, 연극, 뮤지컬 등)"), // 콘서트, 연극, 뮤지컬을 포괄
    CONCERT_WATCHING("콘서트 보기"), // 세분화된 필요가 있다면 유지
    PLAY_WATCHING("연극 보기"),     // 세분화된 필요가 있다면 유지
    MUSICAL_WATCHING("뮤지컬 보기"),   // 세분화된 필요가 있다면 유지
    SPORTS_WATCHING("스포츠 관람하기"),

    // 디지털/온라인 관련
    ONLINE_GAMING("온라인 게임하기"), // PC/콘솔 게임 포함
    MOBILE_GAMING("모바일 게임하기"),
    SOCIAL_MEDIA_USE("SNS 이용하기/글 올리기"),
    BLOGGING("블로그/개인 홈페이지 관리"),

    // 쇼핑/문화생활 관련
    SHOPPING("쇼핑하기"),
    MUSEUM_VISITING("박물관 가기"),
    ART_GALLERY_VISITING("미술관 가기"),
    COFFEE_SHOP_GOING("커피 전문점 가기"),

    // --- 2. 취미/관심사 (Hobbies & Interests) ---
    INSTRUMENT_PLAYING("악기 연주하기"),
    PHOTOGRAPHY("사진 촬영하기"),
    DRAWING_PAINTING("그림 그리기/미술 활동"),
    WRITING_GENERAL("글쓰기 (소설, 시, 수필 등)"),
    READING("독서하기 (책, 잡지, 만화 등)"),
    COOKING("요리하기"),
    GARDENING("정원 가꾸기/화초 기르기"),
    DIY_PROJECTS("DIY 작업하기 (만들기/수리)"),
    PET_RAISING("애완동물 기르기"),
    COLLECTING("수집하기 (우표, 동전 등)"),
    VOLUNTEERING("자원봉사활동"),
    LANGUAGE_STUDY("외국어 학습"),
    CURRENT_AFFAIRS("시사 문제 토론 (뉴스 보기/듣기)"),
    RECYCLING("재활용하기"), // 활동 성격이 강하여 취미/관심사에 포함

    // --- 3. 운동 (Sports) ---
    JOGGING("조깅"),
    WALKING("걷기"),
    SWIMMING("수영"),
    CYCLING("자전거 타기"),
    HIKING("등산 (산책 포함)"),
    FITNESS_GYM("헬스/피트니스 (운동 시설 이용)"),
    YOGA("요가"),
    PILATES("필라테스"),
    SOCCER("축구 (직접 하기)"),
    BASKETBALL("농구 (직접 하기)"),
    BASEBALL_SOFTBALL("야구/소프트볼 (직접 하기)"),
    BADMINTON("배드민턴"),
    TENNIS("테니스"),
    GOLF("골프"),
    SKIING_SNOWBOARDING("스키/스노보드"),
    DANCING("댄스 (춤추기)"),
    TABLE_TENNIS("탁구"),
    BILLIARDS("당구 (포켓볼 포함)"),

    // --- 4. 여행/휴가 (Travel & Vacations) ---
    TRAVEL("여행 (국내/해외)"), // 국내/해외 여행 통합
    BEACH_GOING("해변 가기"), // 여행과 밀접
    PARK_GOING("공원 가기"),   // 여행과 밀접
    CAMPING("캠핑하기"),
    STAYCATION("집에서 보내는 휴가"),

    // --- 5. 직업/학업/기타 (Work/Study & Others) ---
    INDUSTRY("일/산업군 관련"), // 직업 관련
    TECHNOLOGY("기술 관련"), // 기술 관련 (직업/학업과 연관)
    HOUSEWORK("집안일"),
    // 추가될 수 있는 일반적인 질문 카테고리
    GENERAL_INTERESTS("일반적 관심사"), // 특정 카테고리에 속하지 않는 일반적 주제
    PERSONAL_EXPERIENCE("개인적인 경험"), // 개인의 경험을 묻는 질문
    EDUCATION_STUDY("교육/학습"), // 학업 관련
    HEALTH_WELLNESS("건강/웰빙"); // 건강 관련

    private final String label; // 한국어 레이블

    /**
     * Enum의 레이블(한국어 이름)을 반환합니다.
     * @return 한국어 레이블 문자열
     */
    @Override
    public String toString() {
        return this.label;
    }

    /**
     * 문자열 값(영어 상수명)으로부터 SurveyTopic Enum 상수를 찾습니다.
     * 대소문자를 구분하지 않으며, 일치하는 상수가 없으면 null을 반환합니다.
     * @param value Enum 상수의 영어 이름 (예: "MOVIE_WATCHING") 또는 한국어 레이블 (예: "영화 보기")
     * @return 해당 SurveyTopic Enum 상수, 없으면 null
     */
    public static SurveyTopic fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmedValue = value.trim();

        // 1. 영어 상수명으로 찾기 (대소문자 무시)
        try {
            return SurveyTopic.valueOf(trimmedValue.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 2. 한국어 레이블로 찾기 (대소문자 무시)
            for (SurveyTopic topic : values()) {
                if (topic.getLabel().equalsIgnoreCase(trimmedValue)) {
                    return topic;
                }
            }
            return null; // 그래도 없으면 null 반환
        }
    }
}