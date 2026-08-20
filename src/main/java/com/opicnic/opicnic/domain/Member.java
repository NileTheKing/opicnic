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
// DATA-02: 동시 OAuth 콜백(중복 클릭, 네트워크 재시도)이 findByProviderAndProviderId의
// "조회 후 없으면 생성" 사이의 틈을 통과하면 같은 사람 계정이 두 번 생길 수 있었다.
// DB 유니크 제약으로 마지막 방어선을 둔다.
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "providerId"}))
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