package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByNickname(String nickname);

    Optional<Member> findByProviderAndProviderId(String provider, String providerId);
}