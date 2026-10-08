package com.opicnic.opicnic.service.guest;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// "로그인 없이 둘러보기". 기본은 꺼져 있다 — 외부 LLM 무료 한도가 작아서 켜는 쪽이 한도를 의식하고 켜야 한다.
@Component
@ConfigurationProperties(prefix = "opicnic.guest")
@Getter
@Setter
public class GuestProperties {

    public static final String PROVIDER = "guest";

    private boolean enabled = false;
    // 예시 기록의 원본 회원. 비어 있으면 게스트는 빈 상태로 시작한다
    private Long sampleMemberId;
    // 게스트 1명당 하루 채점 문항 수(콤보 1회 = 3문항 안팎). 모의고사는 게스트에게 막혀 있다
    private int dailyQuestions = 6;
    // 모든 게스트 합산 하루 채점 문항 수 — 제공자 무료 한도 보호
    private int globalDailyQuestions = 30;
    // 같은 IP에서 시간당 만들 수 있는 게스트 수 — 새로고침·스크립트로 개인 한도를 우회하는 걸 막는다
    private int ipHourlyCreations = 3;
    private int retentionDays = 7;
}
