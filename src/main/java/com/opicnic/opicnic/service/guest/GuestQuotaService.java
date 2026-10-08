package com.opicnic.opicnic.service.guest;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.exception.RateLimitExceededException;
import com.opicnic.opicnic.repository.ScoringJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 게스트 체험 채점 한도. 외부 LLM 무료 한도가 하루 ~40건이라 게스트가 그걸 다 쓰면 정식 회원이 채점을 못 받는다.
// 별도 카운터를 두지 않고 접수 시각(scoring_job.created_at)을 센다 — 재시작에도 맞고 이중 관리가 없다.
// 읽고 검사하는 사이 동시 접수가 한두 건 더 들어갈 수 있지만 한도의 목적이 절대 상한이 아니라 보호라 락은 두지 않는다.
@Service
@RequiredArgsConstructor
public class GuestQuotaService {

    static final String EXHAUSTED_MESSAGE = "오늘 체험 채점이 마감됐어요. 예시 기록은 계속 볼 수 있어요.";

    private final GuestProperties properties;
    private final ScoringJobRepository jobRepository;

    // 게스트가 아니면 아무것도 하지 않는다
    public void assertCanSubmit(Member member) {
        if (member == null || !GuestProperties.PROVIDER.equals(member.getProvider())) return;
        LocalDateTime since = LocalDate.now().atStartOfDay();
        if (jobRepository.countByMemberIdAndCreatedAtGreaterThanEqual(member.getId(), since) >= properties.getDailySubmissions()
                || jobRepository.countByMemberProviderAndCreatedAtGreaterThanEqual(GuestProperties.PROVIDER, since)
                >= properties.getGlobalDailySubmissions()) {
            throw new RateLimitExceededException(EXHAUSTED_MESSAGE);
        }
    }
}
