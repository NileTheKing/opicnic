package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.dto.StudyApplicationRequestDto;
import com.opicnic.opicnic.dto.StudyApplicationResponseDto;
import com.opicnic.opicnic.service.StudyApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/study-posts")
@Slf4j
public class StudyApplicationController {

    private final StudyApplicationService studyApplicationService;

    // 신청하기
    @PostMapping("/{postId}/apply")
    public ResponseEntity<Long> apply(
            @PathVariable Long postId,
            @RequestBody StudyApplicationRequestDto dto) {

        log.info("appliacing to postId: {}", postId);
        Long id = studyApplicationService.apply(postId, dto.memberId());
        return ResponseEntity.ok(id);
    }

    // 신청자 목록 조회
    @GetMapping("/{postId}/applications")
    public ResponseEntity<List<StudyApplicationResponseDto>> getApplicants(@PathVariable Long postId) {
        List<StudyApplicationResponseDto> result = studyApplicationService.getApplications(postId);
        return ResponseEntity.ok(result);
    }
}