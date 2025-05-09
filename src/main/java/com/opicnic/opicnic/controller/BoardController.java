package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.enums.Region;
import com.opicnic.opicnic.domain.enums.StudyType;
import com.opicnic.opicnic.dto.StudyPostResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/study-posts")
@RequiredArgsConstructor
public class StudyPostController {

    private final StudyPostService studyPostService;

    @GetMapping
    public Page<StudyPostResponseDTO> getStudyPosts(
            @RequestParam(required = false) List<Region> regions,
            @RequestParam(required = false) List<StudyType> studyTypes,
            @RequestParam(required = false) StudyStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return studyPostService.searchPosts(regions, studyTypes, status, keyword, page, size);
    }

    // 상세 조회는 다음 단계에서 추가
}
