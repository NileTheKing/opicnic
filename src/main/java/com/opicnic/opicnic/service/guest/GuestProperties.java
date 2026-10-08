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
    // 게스트 1명당 하루 접수(콤보·모의고사 1회 = 1건) 수
    private int dailySubmissions = 2;
    // 모든 게스트 합산 하루 접수 수 — 제공자 무료 한도 보호
    private int globalDailySubmissions = 10;
    private int retentionDays = 7;
}
