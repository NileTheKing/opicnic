package com.opicnic.opicnic.service.guest;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.job.ScoringJob;
import com.opicnic.opicnic.repository.CoachingReportRepository;
import com.opicnic.opicnic.repository.ExamScheduleRepository;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.FeedbackTagRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.ScoringJobRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.storage.InMemoryAudioStorage;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuestCleanupServiceTest {

    private final GuestProperties properties = new GuestProperties();
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final FeedbackTagRepository tagRepository = mock(FeedbackTagRepository.class);
    private final FeedbackResultRepository resultRepository = mock(FeedbackResultRepository.class);
    private final ScoringJobRepository jobRepository = mock(ScoringJobRepository.class);
    private final CoachingReportRepository reportRepository = mock(CoachingReportRepository.class);
    private final SurveyProfileRepository surveyRepository = mock(SurveyProfileRepository.class);
    private final ExamScheduleRepository examRepository = mock(ExamScheduleRepository.class);
    private final InMemoryAudioStorage storage = new InMemoryAudioStorage();
    private final GuestCleanupService service = new GuestCleanupService(properties, memberRepository, tagRepository,
            resultRepository, jobRepository, reportRepository, surveyRepository, examRepository, storage,
            new TransactionTemplate(mock(PlatformTransactionManager.class)));

    private final Member guest = Member.builder().id(9L).provider("guest").providerId("g").build();

    private ScoringJob jobWithAudio() {
        ScoringJob job = new ScoringJob("att-1", guest, PracticeMode.MOCK_EXAM);
        job.addItem(0, null, "pending/att-1/q0.webm");
        job.addItem(1, 10L, "pending/att-1/q1.webm");
        return job;
    }

    @Test
    void FK_순서대로_지운다_태그_결과_잡_코칭_설문_일정_회원() {
        ScoringJob job = jobWithAudio();
        when(jobRepository.findByMemberId(9L)).thenReturn(List.of(job));
        when(surveyRepository.findByMemberId(9L)).thenReturn(Optional.empty());

        service.deleteGuest(guest);

        InOrder order = inOrder(tagRepository, resultRepository, jobRepository, reportRepository, surveyRepository, examRepository, memberRepository);
        order.verify(tagRepository).deleteByMemberId(9L);
        order.verify(resultRepository).deleteByMemberId(9L);
        order.verify(jobRepository).deleteAll(List.of(job));
        order.verify(reportRepository).deleteByMemberId(9L);
        order.verify(surveyRepository).findByMemberId(9L);
        order.verify(examRepository).deleteByMemberId(9L);
        order.verify(memberRepository).delete(guest);
    }

    @Test
    void 보존기간이_지난_게스트만_조회하고_오디오도_지운다() {
        properties.setRetentionDays(7);
        ScoringJob job = jobWithAudio();
        storage.put("pending/att-1/q0.webm", new byte[]{1});
        storage.put("pending/att-1/q1.webm", new byte[]{1});
        when(memberRepository.findByProviderAndCreatedAtBefore(any(), any())).thenReturn(List.of(guest));
        when(jobRepository.findByMemberId(9L)).thenReturn(List.of(job));
        when(surveyRepository.findByMemberId(9L)).thenReturn(Optional.empty());

        LocalDateTime before = LocalDateTime.now();
        service.cleanupExpired();

        org.mockito.ArgumentCaptor<LocalDateTime> cutoff = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(memberRepository).findByProviderAndCreatedAtBefore(org.mockito.ArgumentMatchers.eq("guest"), cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before.minusDays(7).minusSeconds(1), LocalDateTime.now().minusDays(7));
        verify(memberRepository).delete(guest);
        assertThat(storage.exists("pending/att-1/q0.webm")).isFalse();
        assertThat(storage.exists("pending/att-1/q1.webm")).isFalse();
    }

    @Test
    void 한_명이_실패해도_다음_게스트는_지운다() {
        Member other = Member.builder().id(10L).provider("guest").providerId("h").build();
        when(memberRepository.findByProviderAndCreatedAtBefore(any(), any())).thenReturn(List.of(guest, other));
        when(jobRepository.findByMemberId(any())).thenReturn(List.of());
        doThrow(new IllegalStateException("boom")).when(tagRepository).deleteByMemberId(9L);

        service.cleanupExpired();

        verify(memberRepository, never()).delete(guest);
        verify(memberRepository).delete(other);
    }
}
