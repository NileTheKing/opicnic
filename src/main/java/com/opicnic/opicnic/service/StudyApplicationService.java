package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.StudyApplication;
import com.opicnic.opicnic.domain.StudyPost;
import com.opicnic.opicnic.domain.enums.ApplicationStatus;
import com.opicnic.opicnic.dto.StudyApplicationResponseDto;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.StudyApplicationRepository;
import com.opicnic.opicnic.repository.StudyPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudyApplicationService {

    private final StudyApplicationRepository applicationRepository;
    private final StudyPostRepository studyPostRepository;
    private final MemberRepository memberRepository;

    public Long apply(Long postId, Long memberId) {
        // 중복 신청 방지
        if (applicationRepository.existsByPostIdAndApplicantId(postId, memberId)) {
            throw new IllegalArgumentException("이미 신청한 스터디입니다.");
        }

        StudyPost post = studyPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 스터디입니다."));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        StudyApplication application = StudyApplication.builder()
                .post(post)
                .applicant(member)
                .status(ApplicationStatus.PENDING)
                .appliedAt(LocalDateTime.now().toString())
                .build();

        applicationRepository.save(application);
        return application.getId();
    }

    public List<StudyApplicationResponseDto> getApplications(Long postId) {
        return applicationRepository.findByPostId(postId).stream()
                .map(StudyApplicationResponseDto::from)
                .collect(Collectors.toList());
    }
}