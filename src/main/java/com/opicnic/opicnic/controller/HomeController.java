package com.opicnic.opicnic.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Slf4j
public class HomeController {

    @GetMapping("/")
    public String redirectToPractice() {
        // 리디렉션을 사용하여 /practice로 이동
        return "redirect:/practice";
    }

    @GetMapping("/practice")
    public String practicePage() {
        // practice.html 템플릿을 렌더링
        return "/practice/practice";
    }
}
