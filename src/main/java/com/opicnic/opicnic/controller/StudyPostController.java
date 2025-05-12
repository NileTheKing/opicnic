package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.enums.Region;
import com.opicnic.opicnic.domain.enums.StudyType;
import com.opicnic.opicnic.dto.StudyPostRequestDto;
import com.opicnic.opicnic.dto.StudyPostResponseDto;
import com.opicnic.opicnic.service.MemberService;
import com.opicnic.opicnic.service.StudyPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/study-posts")
@RequiredArgsConstructor
@Slf4j
public class StudyPostController {

    private final StudyPostService studyPostService;
    private final MemberService memberService;

    // 게시글 목록 조회 (옵션: 지역, 스터디유형으로 필터링)
    @GetMapping
    public ResponseEntity<List<StudyPostResponseDto>> getAll(
            @RequestParam(required = false) List<Region> regions,
            @RequestParam(required = false) List<StudyType> studyTypes) {
        return ResponseEntity.ok(studyPostService.findAllFiltered(regions, studyTypes));
    }

    // 단건 조회
    @GetMapping("/{id}")
    public ResponseEntity<StudyPostResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(studyPostService.findById(id));
    }

    // 게시글 생성
    @PostMapping
    public ResponseEntity<Long> create(@RequestBody StudyPostRequestDto requestDto, @AuthenticationPrincipal OAuth2User principal) {
        if (principal != null) {
            Map<String, Object> attributes = principal.getAttributes();
            String provider = (String) attributes.get("provider"); // "kakao"
            String providerId = principal.getName(); // Kakao의 providerId (일반적으로 attributes의 "id" 값)

            // provider와 providerId로 Member 조회
            Member writer = memberService.findByProviderAndProviderId(provider, providerId)
                    .orElse(null); // 또는 예외 처리

            if (writer != null) {
                Long postId = studyPostService.createPost(requestDto, writer);
                return ResponseEntity.ok(postId);
            } else {
                log.warn("OAuth2Principal로부터 Member 정보를 찾을 수 없습니다.");
                return ResponseEntity.status(401).build(); // Unauthorized
            }
        } else {
            log.warn("인증되지 않은 사용자의 게시글 생성 시도");
            return ResponseEntity.status(401).build(); // Unauthorized
        }
    }
    // 게시글 수정
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id, @RequestBody StudyPostRequestDto requestDto) {
        studyPostService.updatePost(id, requestDto);
        return ResponseEntity.noContent().build();
    }

    // 게시글 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        studyPostService.deletePost(id);
        return ResponseEntity.noContent().build();
    }
}