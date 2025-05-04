package com.opicnic.opicnic.domain;


import com.opicnic.opicnic.domain.enums.Role;
import jakarta.persistence.*;
import lombok.*;


@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor  // 이거 추가!
@Entity
public class Member {

    @Id
    @GeneratedValue
    @Column(name = "member_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;

    private String nickname;

    private String provider; // OAuth 로그인 제공자 (ex: kakao, google 등)
    private String providerId; // OAuth 로그인 사용자 ID or 내부 사용자 ID

    @OneToOne(mappedBy = "member", cascade = CascadeType.ALL)
    private NotificationSetting notificationSetting;

    // 필요시 일반 로그인도 지원할 경우 아래 필드를 남김
    // private String password;

}