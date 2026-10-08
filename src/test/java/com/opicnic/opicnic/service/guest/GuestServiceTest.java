package com.opicnic.opicnic.service.guest;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.enums.Role;
import com.opicnic.opicnic.domain.SurveyProfile;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import com.opicnic.opicnic.service.SurveyTopicPolicy;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 게스트 회원 생성과 principal 모양. 기존 컨트롤러는 전부 OAuth2User의 provider/providerId 속성과 getName()으로 회원을 찾는다.
class GuestServiceTest {

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final GuestSampleCopier sampleCopier = mock(GuestSampleCopier.class);
    private final GuestProperties properties = new GuestProperties();
    private final SurveyProfileRepository surveyProfileRepository = mock(SurveyProfileRepository.class);
    private final GuestService service = new GuestService(memberRepository, properties, sampleCopier, surveyProfileRepository);

    GuestServiceTest() {
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> {
            Member m = inv.getArgument(0);
            m.setId(42L);
            return m;
        });
    }

    @Test
    void 방문자마다_provider_guest의_새_USER_회원이_만들어진다() {
        Member a = service.createGuest();
        Member b = service.createGuest();

        assertThat(a.getProvider()).isEqualTo("guest");
        assertThat(a.getNickname()).isEqualTo("게스트");
        assertThat(a.getRole()).isEqualTo(Role.USER);
        assertThat(a.getProviderId()).isNotEqualTo(b.getProviderId());
        // NotificationSetting은 FK 소유 쪽에도 member가 들어가야 한다
        assertThat(a.getNotificationSetting().getMember()).isSameAs(a);
    }

    @Test
    void principal은_카카오_로그인과_같은_속성_이름을_가진다() {
        Member guest = service.createGuest();

        OAuth2AuthenticationToken auth = service.toAuthentication(guest);

        assertThat(auth.isAuthenticated()).isTrue();
        OAuth2User principal = auth.getPrincipal();
        assertThat((String) principal.getAttribute("provider")).isEqualTo("guest");
        assertThat((String) principal.getAttribute("providerId")).isEqualTo(guest.getProviderId());
        assertThat(principal.getName()).isEqualTo(guest.getProviderId());
        assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("USER");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).doesNotContain("ADMIN");
    }

    @Test
    void 예시_원본이_설정되면_복사하고_없으면_건너뛴다() {
        service.createGuest();
        verify(sampleCopier, never()).copy(any(), any(), any());

        properties.setSampleMemberId(5L);
        Member guest = service.createGuest();
        verify(sampleCopier).copy(eq(5L), eq(guest), any(LocalDateTime.class));
    }

    @Test
    void 설문이_없으면_온보딩_전체선택과_같은_기본_설문을_만든다() {
        when(surveyProfileRepository.findByMemberId(42L)).thenReturn(Optional.empty());

        service.createGuest();

        ArgumentCaptor<SurveyProfile> saved = ArgumentCaptor.forClass(SurveyProfile.class);
        verify(surveyProfileRepository).save(saved.capture());
        SurveyProfile p = saved.getValue();
        assertThat(p.getTargetGrade()).isEqualTo(SurveyProfile.TargetGrade.IH);
        assertThat(p.getResidenceType()).isEqualTo(SurveyProfile.ResidenceType.ALONE);
        assertThat(new SurveyTopicPolicy().isValid(SurveyTopicPolicy.allTopics())).isTrue();
        assertThat(p.getSelectedTopics()).containsAll(SurveyTopicPolicy.allTopics()).contains(SurveyTopic.LIVING_ALONE);
    }

    @Test
    void 예시_설문이_복사됐으면_기본_설문을_만들지_않는다() {
        when(surveyProfileRepository.findByMemberId(42L)).thenReturn(Optional.of(SurveyProfile.builder().build()));

        service.createGuest();

        verify(surveyProfileRepository, never()).save(any());
    }
}
