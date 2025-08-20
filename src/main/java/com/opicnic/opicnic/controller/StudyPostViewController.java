package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.dto.StudyPostResponseDto;
import com.opicnic.opicnic.service.StudyPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/studypost")
@Slf4j
@RequiredArgsConstructor
public class StudyPostViewController {
    private final StudyPostService studyPostService;

    @GetMapping
    public String studyPostBoard(Model model) {
        List<StudyPostResponseDto> posts = studyPostService.findAllFiltered(null, null, null);
        model.addAttribute("posts", posts);
        return "/studypost/board";
    }

    @GetMapping("/{id}")
    public String studyPostDetail(@PathVariable Long id, Model model) {
        model.addAttribute("postId", id); // JS에서 fetch용으로 사용 가능
        log.info("postId: {}", id);
        return "/studypost/detail";
    }

    @GetMapping("/new")
    public String studyPostWrite() {
        return "/studypost/study-post-write";
    }

    @GetMapping("/edit-post/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        log.info("Edit post ID: {}", id);
        StudyPostResponseDto post = studyPostService.findById(id);
        model.addAttribute("post", post);
        // 필요한 Enum 정보 등도 model에 추가
        return "/studypost/edit-post"; // edit-post.html 또는 해당 템플릿 파일 이름
    }
}
