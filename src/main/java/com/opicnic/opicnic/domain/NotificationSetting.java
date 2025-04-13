package com.opicnic.opicnic.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class NotificationSetting {
    @Id
    @GeneratedValue
    private Long id;

    private boolean examScheduleNotification = false;
    private boolean reviewNotification = false;
    private boolean studyBoardNotification = false;

    @OneToOne
    @JoinColumn(name = "member_id")
    @Setter
    private Member member;
}