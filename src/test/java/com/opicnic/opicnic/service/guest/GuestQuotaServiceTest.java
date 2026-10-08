package com.opicnic.opicnic.service.guest;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.exception.RateLimitExceededException;
import com.opicnic.opicnic.repository.ScoringJobRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GuestQuotaServiceTest {

    private final ScoringJobRepository jobRepository = mock(ScoringJobRepository.class);
    private final GuestProperties properties = new GuestProperties();   // 기본 2 / 10
    private final GuestQuotaService quota = new GuestQuotaService(properties, jobRepository);
    private final Member guest = Member.builder().id(1L).provider("guest").providerId("g").build();

    private void counts(long mine, long all) {
        when(jobRepository.countByMemberIdAndCreatedAtGreaterThanEqual(eq(1L), any(LocalDateTime.class))).thenReturn(mine);
        when(jobRepository.countByMemberProviderAndCreatedAtGreaterThanEqual(eq("guest"), any(LocalDateTime.class))).thenReturn(all);
    }

    @Test
    void 한도_안이면_통과() {
        counts(1, 9);
        assertThatCode(() -> quota.assertCanSubmit(guest)).doesNotThrowAnyException();
    }

    @Test
    void 개인_한도에_도달하면_안내_문구와_함께_거절() {
        counts(2, 2);
        assertThatThrownBy(() -> quota.assertCanSubmit(guest))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("오늘 체험 채점이 마감됐어요. 예시 기록은 계속 볼 수 있어요.");
    }

    @Test
    void 전체_한도에_도달하면_개인_한도가_남아도_거절() {
        counts(0, 10);
        assertThatThrownBy(() -> quota.assertCanSubmit(guest)).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void 한도는_설정으로_바뀐다() {
        properties.setDailySubmissions(3);
        counts(2, 2);
        assertThatCode(() -> quota.assertCanSubmit(guest)).doesNotThrowAnyException();
    }

    @Test
    void 게스트가_아니면_저장소를_보지도_않는다() {
        Member kakao = Member.builder().id(2L).provider("kakao").providerId("k").build();
        assertThatCode(() -> quota.assertCanSubmit(kakao)).doesNotThrowAnyException();
        assertThatCode(() -> quota.assertCanSubmit(null)).doesNotThrowAnyException();
        verifyNoInteractions(jobRepository);
    }
}
