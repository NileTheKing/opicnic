package com.opicnic.opicnic.service.guest;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.job.ScoringJob;
import com.opicnic.opicnic.domain.job.ScoringJobItem;
import com.opicnic.opicnic.repository.CoachingReportRepository;
import com.opicnic.opicnic.repository.ExamScheduleRepository;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.FeedbackTagRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.ScoringJobRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.storage.AudioStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 만료된 게스트와 그 기록을 지운다. FK 순서: 태그 -> 결과 -> 잡(문항 cascade) -> 코칭 -> 설문 -> 일정 -> 회원(알림설정 cascade).
// 회원 단위 트랜잭션 — 한 명이 실패해도 나머지는 지워진다.
@Service
@RequiredArgsConstructor
@Slf4j
public class GuestCleanupService {

    private final GuestProperties properties;
    private final MemberRepository memberRepository;
    private final FeedbackTagRepository feedbackTagRepository;
    private final FeedbackResultRepository feedbackResultRepository;
    private final ScoringJobRepository jobRepository;
    private final CoachingReportRepository coachingReportRepository;
    private final SurveyProfileRepository surveyProfileRepository;
    private final ExamScheduleRepository examScheduleRepository;
    private final AudioStorage audioStorage;
    private final TransactionTemplate tx;

    // 게스트 기능을 꺼도 이미 만들어진 게스트는 정리돼야 하므로 enabled와 무관하게 돈다(없으면 조회만 하고 끝)
    @Scheduled(cron = "${opicnic.guest.cleanup-cron:0 30 4 * * *}")
    public void cleanupExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(properties.getRetentionDays());
        List<Member> expired = memberRepository.findByProviderAndCreatedAtBefore(GuestProperties.PROVIDER, cutoff);
        int deleted = 0;
        for (Member guest : expired) {
            try {
                List<String> audioKeys = tx.execute(status -> deleteGuest(guest));
                deleteAudio(audioKeys);
                deleted++;
            } catch (RuntimeException e) {
                log.warn("[Guest] 정리 실패 member={}", guest.getId(), e);
            }
        }
        if (!expired.isEmpty()) log.info("[Guest] 만료 게스트 정리 {}/{}", deleted, expired.size());
    }

    // 트랜잭션 안에서 DB 행만 지우고 지울 R2 키를 돌려준다(커밋 뒤에 지운다 — 롤백됐는데 오디오만 사라지는 걸 막는다)
    List<String> deleteGuest(Member guest) {
        Long id = guest.getId();
        List<ScoringJob> jobs = jobRepository.findByMemberId(id);
        List<String> audioKeys = new ArrayList<>();
        for (ScoringJob job : jobs) {
            for (ScoringJobItem item : job.getItems()) audioKeys.add(item.getAudioKey());
        }
        feedbackTagRepository.deleteByMemberId(id);
        feedbackResultRepository.deleteByMemberId(id);
        jobRepository.deleteAll(jobs);
        coachingReportRepository.deleteByMemberId(id);
        surveyProfileRepository.findByMemberId(id).ifPresent(surveyProfileRepository::delete);
        examScheduleRepository.deleteByMemberId(id);
        memberRepository.delete(guest);
        return audioKeys;
    }

    // R2 객체는 best-effort. 못 지워도 pending/는 라이프사이클이 1일 뒤 치운다
    private void deleteAudio(List<String> keys) {
        if (keys == null) return;
        for (String key : keys) {
            try {
                audioStorage.delete(key);
            } catch (RuntimeException e) {
                log.debug("[Guest] 오디오 삭제 실패(무시) key={}", key, e);
            }
        }
    }
}
