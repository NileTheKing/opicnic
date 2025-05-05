package com.opicnic.opicnic.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/studypost")
public class StudyController {

    @GetMapping("/board")
    public String studyBoard() {
        return "/studypost/board";
    }
}
