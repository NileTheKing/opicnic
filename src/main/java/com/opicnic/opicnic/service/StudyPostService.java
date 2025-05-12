package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.StudyPost;
import com.opicnic.opicnic.domain.enums.Region;
import com.opicnic.opicnic.domain.enums.StudyStatus;
import com.opicnic.opicnic.domain.enums.StudyType;
import com.opicnic.opicnic.dto.StudyPostRequestDto;
import com.opicnic.opicnic.dto.StudyPostResponseDto;
import com.opicnic.opicnic.repository.StudyPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudyPostService {

    private final StudyPostRepository studyPostRepository;

    public List<StudyPostResponseDto> findAllFiltered(List<Region> regions, List<StudyType> studyTypes) {
        List<StudyPost> posts = studyPostRepository.findFiltered(regions, studyTypes);
        return posts.stream()
                .map(StudyPostResponseDto::from)
                .collect(Collectors.toList());
    }

    public StudyPostResponseDto findById(Long id) {
        StudyPost post = studyPostRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
        return StudyPostResponseDto.from(post);
    }

    @Transactional
    public Long createPost(StudyPostRequestDto requestDto, Member writer) {
        StudyPost studyPost = requestDto.toEntity();
        studyPost.setWriter(writer); // 작성자 설정
        studyPost.setStatus(StudyStatus.RECRUITING); // 기본 상태 설정
        studyPost.setCurrentParticipants(1); // 현재 참가자 수 초기화
        StudyPost savedPost = studyPostRepository.save(studyPost);
        return savedPost.getId();
    }

    public void updatePost(Long id, StudyPostRequestDto dto) {
        StudyPost post = studyPostRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
        post.update(dto);
        studyPostRepository.save(post);
    }

    public void deletePost(Long id) {
        studyPostRepository.deleteById(id);
    }
}