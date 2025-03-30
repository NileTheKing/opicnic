package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class MemberService {

    private final MemberRepository memberRepository;


    @Transactional
    public Long join(Member member) {
        validateDuplicateMember(member);
        member.setPassword(member.getPassword());
        memberRepository.save(member);
        log.info("멤버 등록 호출");
        return member.getId();
    }

    public void validateDuplicateMember(Member member) {
        List<Member> byName = memberRepository.findByName(member.getName());
        if (!byName.isEmpty()) {
            throw new IllegalStateException("이미 존재하는 회원입니다.");
        }
    }

    public List<Member> findMembers() {
        return memberRepository.findAll();
    }

}
