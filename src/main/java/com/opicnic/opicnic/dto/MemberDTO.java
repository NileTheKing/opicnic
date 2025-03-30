package com.opicnic.opicnic.dto;


import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class MemberDTO {

    @NotEmpty(message = "이름은 필수입니다.")
    private String name;
    @NotEmpty(message = "비밀번호는 필수입니다.")
    private String password;
    @NotEmpty(message = "email은 필수입니다.")
    private String email;
}
