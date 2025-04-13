package com.opicnic.opicnic.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSetting {
    @Id
    @GeneratedValue
    private Long id;

    private boolean examScheduleNotification = false;
    private boolean reviewNotification = false;
    private boolean studyBoardNotification = false;

    @OneToOne
    @JoinColumn(name = "member_id")
    private Member member;
}