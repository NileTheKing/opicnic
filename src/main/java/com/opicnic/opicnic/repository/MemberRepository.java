package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.Member;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MemberRepository {
    private final EntityManager em;

    public void save(Member member) {
        em.persist(member);
    }

    public Member findOne(Long id) {
        return em.find(Member.class, id);
    }

    public Optional<Member> findByUserId(String userId) {
        try {
            return Optional.of(em.createQuery("select m from Member m where m.userId = :userId", Member.class)
                    .setParameter("userId", userId)
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }


    public List<Member> findAll() {
        return em.createQuery("select m from Member m", Member.class).getResultList();
    }

    public List<Member> findByName(String name) {
        return em.createQuery("select m from Member m where m.name = :name", Member.class)
                .setParameter("name", name)
                .getResultList();
    }
}
