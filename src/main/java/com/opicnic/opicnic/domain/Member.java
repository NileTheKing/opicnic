package com.opicnic.opicnic.domain;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;


@Getter @Setter
@Entity

public class Member {

    @Id
    @GeneratedValue
    @Column(name = "member_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;

    private String name;
    private String userId;
    private String password;
    private String email;

    /*@OneToOne(mappedBy = "member", cascade = CascadeType.ALL)
    private NotificationSetting notificationSetting;
*/

}
