package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.dto.MemberDTO;
import com.opicnic.opicnic.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/members/new")
    public String createForm(Model model) {
        model.addAttribute("memberForm", new MemberDTO());
        log.info("회원가입 폼 호출");
        return "members/createMemberForm";
    }

    @PostMapping("members/new")
    public String create(@Valid MemberDTO form, BindingResult result) {

        if (result.hasErrors()) {
            return "members/createMemberForm";
        }

        Member member = new Member();

        member.setName(form.getName());
        member.setPassword(form.getPassword());
        member.setEmail(form.getEmail());

        memberService.join(member);
        log.info("회원가입 호출");

        return "redirect:/";
    }
}
