package com.opicnic.opicnic.service.guest;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.exception.RateLimitExceededException;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.ScoringJobItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GuestQuotaServiceTest {

    private final ScoringJobItemRepository itemRepository = mock(ScoringJobItemRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final GuestProperties properties = new GuestProperties();   // 기본 6 / 30
    private final GuestQuotaService quota = new GuestQuotaService(properties, itemRepository, memberRepository);
    private final Member guest = Member.builder().id(1L).provider("guest").providerId("g").build();

    private void counts(long mine, long all) {
        when(itemRepository.countByJobMemberIdAndJobCreatedAtGreaterThanEqual(eq(1L), any(LocalDateTime.class))).thenReturn(mine);
        when(itemRepository.countByJobMemberProviderAndJobCreatedAtGreaterThanEqual(eq("guest"), any(LocalDateTime.class))).thenReturn(all);
    }

    @Test
    void 남은_문항_안이면_통과() {
        counts(3, 20);
        assertThat(quota.remaining(guest)).isEqualTo(3);
        assertThatCode(() -> quota.assertCanSubmit(guest, PracticeMode.COMBO, 3)).doesNotThrowAnyException();
    }

    @Test
    void 개인_한도를_넘기는_제출은_안내_문구와_함께_거절() {
        counts(4, 4);   // 6 - 4 = 2문항 남음, 콤보는 3문항
        assertThatThrownBy(() -> quota.assertCanSubmit(guest, PracticeMode.COMBO, 3))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("오늘 체험 채점이 마감됐어요. 예시 기록은 계속 볼 수 있어요.");
    }

    @Test
    void 전체_한도가_차면_개인_한도가_남아도_거절() {
        counts(0, 29);
        assertThat(quota.remaining(guest)).isEqualTo(1);
        assertThatThrownBy(() -> quota.assertCanSubmit(guest, PracticeMode.COMBO, 3)).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void 한도는_설정으로_바뀐다() {
        properties.setDailyQuestions(9);
        counts(6, 6);
        assertThatCode(() -> quota.assertCanSubmit(guest, PracticeMode.COMBO, 3)).doesNotThrowAnyException();
    }

    @Test
    void 게스트의_모의고사는_한도와_무관하게_막힌다() {
        counts(0, 0);
        assertThatThrownBy(() -> quota.assertCanSubmit(guest, PracticeMode.MOCK_EXAM, 15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("모의고사는 카카오 로그인 후 이용할 수 있어요");
    }

    @Test
    void 게스트가_아니면_저장소를_보지도_않는다() {
        Member kakao = Member.builder().id(2L).provider("kakao").providerId("k").build();
        assertThatCode(() -> quota.assertCanSubmit(kakao, PracticeMode.MOCK_EXAM, 15)).doesNotThrowAnyException();
        assertThatCode(() -> quota.assertCanSubmit(null, PracticeMode.COMBO, 3)).doesNotThrowAnyException();
        verifyNoInteractions(itemRepository);
    }

    @Test
    void 시작_화면에_남은_문항과_시작_가능_여부를_싣는다() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(guest));
        counts(4, 4);

        ExtendedModelMap model = new ExtendedModelMap();
        quota.addStartAttributes(1L, 3, model);   // 2문항 남음 < 3문항 콤보
        assertThat(model.get("guestRemaining")).isEqualTo(2);
        assertThat(model.get("guestBlocked")).isEqualTo(true);

        ExtendedModelMap single = new ExtendedModelMap();
        quota.addStartAttributes(1L, 1, single);   // 유형별 연습 1문항은 가능
        assertThat(single.get("guestBlocked")).isEqualTo(false);
    }

    @Test
    void 시작_화면_속성은_게스트에게만_싣는다() {
        Member kakao = Member.builder().id(2L).provider("kakao").providerId("k").build();
        when(memberRepository.findById(2L)).thenReturn(Optional.of(kakao));

        ExtendedModelMap model = new ExtendedModelMap();
        quota.addStartAttributes(2L, 3, model);
        quota.addStartAttributes(null, 3, model);

        assertThat(model).isEmpty();
    }
}
