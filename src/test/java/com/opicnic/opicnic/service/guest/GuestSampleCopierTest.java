package com.opicnic.opicnic.service.guest;

import com.opicnic.opicnic.domain.CoachingReport;
import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.FeedbackTag;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.repository.CoachingReportRepository;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.FeedbackTagRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuestSampleCopierTest {

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final SurveyProfileRepository surveyProfileRepository = mock(SurveyProfileRepository.class);
    private final FeedbackResultRepository resultRepository = mock(FeedbackResultRepository.class);
    private final FeedbackTagRepository tagRepository = mock(FeedbackTagRepository.class);
    private final CoachingReportRepository reportRepository = mock(CoachingReportRepository.class);
    private final GuestSampleCopier copier = new GuestSampleCopier(
            memberRepository, surveyProfileRepository, resultRepository, tagRepository, reportRepository);

    private final Member sample = Member.builder().id(5L).provider("kakao").build();
    private final Member guest = Member.builder().id(9L).provider("guest").build();
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 8, 12, 0);

    private FeedbackResult source(long id, String attemptId, LocalDateTime at) {
        return FeedbackResult.builder().id(id).member(sample).attemptId(attemptId).overallGrade("IM2")
                .sttText("hello").createdAt(at).build();
    }

    @Test
    void 가장_최근_기록이_지금이_되도록_간격을_유지한_채_밀고_예시로_표시한다() {
        when(memberRepository.findById(5L)).thenReturn(Optional.of(sample));
        FeedbackResult newest = source(2, "A", LocalDateTime.of(2026, 9, 20, 10, 0));
        FeedbackResult older = source(1, "B", LocalDateTime.of(2026, 9, 18, 10, 0));
        when(resultRepository.findByMemberIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(newest, older));
        AtomicLong ids = new AtomicLong(100);
        when(resultRepository.save(any(FeedbackResult.class))).thenAnswer(inv -> {
            FeedbackResult r = inv.getArgument(0);
            return FeedbackResult.builder().id(ids.incrementAndGet()).member(r.getMember()).attemptId(r.getAttemptId())
                    .sample(r.getSample()).build();
        });
        when(tagRepository.findByFeedbackResultIdIn(any())).thenReturn(List.of(
                FeedbackTag.builder().feedbackResult(newest).category("mainPoint").tag("WHY_MISSING").build()));

        copier.copy(5L, guest, now);

        ArgumentCaptor<FeedbackResult> saved = ArgumentCaptor.forClass(FeedbackResult.class);
        verify(resultRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(r -> {
            assertThat(r.getMember()).isSameAs(guest);
            assertThat(r.getSample()).isTrue();
            assertThat(r.getAttemptId()).isNotIn("A", "B");   // 원본 attemptId와 분리
        });
        // newest(id 101)는 now, older(id 102)는 그보다 2일 전 — 상대 간격 유지
        verify(resultRepository).updateCreatedAt(101L, now);
        verify(resultRepository).updateCreatedAt(102L, now.minusDays(2));

        ArgumentCaptor<List<FeedbackTag>> tags = ArgumentCaptor.forClass(List.class);
        verify(tagRepository).saveAll(tags.capture());
        assertThat(tags.getValue()).singleElement().satisfies(t -> {
            assertThat(t.getTag()).isEqualTo("WHY_MISSING");
            assertThat(t.getFeedbackResult().getId()).isEqualTo(101L);   // 복사본에 붙는다
        });
    }

    @Test
    void 최신_코칭_리포트를_지금_시각으로_복사한다() {
        when(memberRepository.findById(5L)).thenReturn(Optional.of(sample));
        when(resultRepository.findByMemberIdOrderByCreatedAtDesc(5L)).thenReturn(List.of());
        when(reportRepository.findTopByMemberIdOrderByCreatedAtDesc(5L)).thenReturn(Optional.of(
                CoachingReport.builder().member(sample).content("{}").basedOnCount(12).build()));
        when(reportRepository.save(any(CoachingReport.class))).thenAnswer(inv -> {
            CoachingReport r = inv.getArgument(0);
            return CoachingReport.builder().id(77L).member(r.getMember()).content(r.getContent()).basedOnCount(r.getBasedOnCount()).build();
        });

        copier.copy(5L, guest, now);

        ArgumentCaptor<CoachingReport> saved = ArgumentCaptor.forClass(CoachingReport.class);
        verify(reportRepository).save(saved.capture());
        assertThat(saved.getValue().getMember()).isSameAs(guest);
        assertThat(saved.getValue().getBasedOnCount()).isEqualTo(12);
        verify(reportRepository).updateCreatedAt(77L, now);
    }

    @Test
    void 원본_회원이_없으면_아무것도_복사하지_않는다() {
        when(memberRepository.findById(5L)).thenReturn(Optional.empty());

        copier.copy(5L, guest, now);

        verify(resultRepository, never()).save(any());
        verify(reportRepository, never()).save(any());
        verify(resultRepository, never()).updateCreatedAt(anyLong(), any());
    }
}
