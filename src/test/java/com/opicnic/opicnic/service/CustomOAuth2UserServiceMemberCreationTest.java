package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// DATA-02 회귀 테스트:
// 1) 신규 회원 생성 시 NotificationSetting의 FK 소유 쪽(member)이 실제로 채워지는지
//    (예전엔 member.setNotificationSetting()만 해서 member_id가 null로 저장됐다)
// 2) 동시 OAuth 콜백이 존재 여부 체크를 둘 다 통과해 save()가 유니크 제약을 위반해도,
//    500 대신 이미 다른 요청이 만든 회원으로 조용히 수렴하는지
class CustomOAuth2UserServiceMemberCreationTest {

    @Test
    void createMemberSetsOwningSideOfNotificationSetting() {
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        CustomOAuth2UserService service = new CustomOAuth2UserService(memberRepository);
        when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Member member = service.createMember("kakao", "provider-id-1", "닉네임");

        assertThat(member.getNotificationSetting()).isNotNull();
        assertThat(member.getNotificationSetting().getMember()).isSameAs(member);
    }

    @Test
    void concurrentCallbackFallsBackToExistingMemberOnUniqueConstraintViolation() {
        MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
        CustomOAuth2UserService service = new CustomOAuth2UserService(memberRepository);

        Member alreadyCreatedByOtherRequest = Member.builder()
                .id(1L).provider("kakao").providerId("provider-id-1").build();

        when(memberRepository.save(any())).thenThrow(new DataIntegrityViolationException("unique constraint"));
        when(memberRepository.findByProviderAndProviderId("kakao", "provider-id-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(alreadyCreatedByOtherRequest));

        Member result = service.findOrCreateMember("kakao", "provider-id-1", "닉네임");

        assertThat(result).isSameAs(alreadyCreatedByOtherRequest);
    }
}
