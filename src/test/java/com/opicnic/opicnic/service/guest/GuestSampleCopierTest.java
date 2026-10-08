package com.opicnic.opicnic.service.guest;

import com.opicnic.opicnic.domain.CoachingReport;
import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.FeedbackTag;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.repository.CoachingReportRepository;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.FeedbackTagRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuestSampleCopierTest {

    private final FeedbackResultRepository resultRepository = mock(FeedbackResultRepository.class);
    private final FeedbackTagRepository tagRepository = mock(FeedbackTagRepository.class);
    private final CoachingReportRepository reportRepository = mock(CoachingReportRepository.class);
    private final GuestSampleCopier copier = new GuestSampleCopier(resultRepository, tagRepository, reportRepository);

    private final Member guest = Member.builder().id(9L).provider("guest").build();
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 8, 12, 0);

    private static GuestSample.Result result(String attemptId, LocalDateTime at, List<GuestSample.Tag> tags) {
        return new GuestSample.Result(51L, attemptId, null, "PARK_GOING", "TYPE_1,TYPE_2,TYPE_3", "C1", "q", "hello",
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, "IM2", null, null, null, at, tags);
    }

    @Test
    void 가장_최근_기록이_지금이_되도록_간격을_유지한_채_밀고_예시로_표시한다() {
        AtomicLong ids = new AtomicLong(100);
        when(resultRepository.save(any(FeedbackResult.class))).thenAnswer(inv -> {
            FeedbackResult r = inv.getArgument(0);
            return FeedbackResult.builder().id(ids.incrementAndGet()).member(r.getMember()).attemptId(r.getAttemptId())
                    .sample(r.getSample()).build();
        });
        GuestSample sample = new GuestSample(List.of(
                result("A", LocalDateTime.of(2026, 9, 20, 10, 0), List.of(new GuestSample.Tag("mainPoint", "WHY_MISSING"))),
                result("A", LocalDateTime.of(2026, 9, 18, 10, 0), List.of())), null);

        copier.copy(sample, guest, now);

        ArgumentCaptor<FeedbackResult> saved = ArgumentCaptor.forClass(FeedbackResult.class);
        verify(resultRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(r -> {
            assertThat(r.getMember()).isSameAs(guest);
            assertThat(r.getSample()).isTrue();
            assertThat(r.getAttemptId()).isNotEqualTo("A");   // 파일의 attemptId와 분리
        });
        // 같은 콤보는 같은 새 attemptId로 묶인다
        assertThat(saved.getAllValues().get(0).getAttemptId()).isEqualTo(saved.getAllValues().get(1).getAttemptId());
        // 최신(101)은 now, 다른 하나(102)는 2일 전 — 상대 간격 유지
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
    void 코칭_리포트를_지금_시각으로_복사한다() {
        when(reportRepository.save(any(CoachingReport.class))).thenAnswer(inv -> {
            CoachingReport r = inv.getArgument(0);
            return CoachingReport.builder().id(77L).member(r.getMember()).content(r.getContent()).basedOnCount(r.getBasedOnCount()).build();
        });

        copier.copy(new GuestSample(List.of(), new GuestSample.Report("{}", 12)), guest, now);

        ArgumentCaptor<CoachingReport> saved = ArgumentCaptor.forClass(CoachingReport.class);
        verify(reportRepository).save(saved.capture());
        assertThat(saved.getValue().getMember()).isSameAs(guest);
        assertThat(saved.getValue().getBasedOnCount()).isEqualTo(12);
        verify(reportRepository).updateCreatedAt(77L, now);
        verify(resultRepository, never()).save(any());
    }
}
