package com.opicnic.opicnic.service.guest;

import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.exception.RateLimitExceededException;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.ScoringJobItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 게스트 체험 채점 한도. 외부 LLM 무료 한도가 하루 ~40건이라 게스트가 그걸 다 쓰면 정식 회원이 채점을 못 받는다.
// 문항(scoring_job_item) 단위로 센다 — 모의고사 한 번이 15문항이라 접수 건수로는 한도가 의미가 없었다.
// 별도 카운터 없이 접수 시각을 센다 — 재시작에도 맞고 이중 관리가 없다.
// 읽고 검사하는 사이 동시 접수가 한두 건 더 들어갈 수 있지만 한도의 목적이 절대 상한이 아니라 보호라 락은 두지 않는다.
@Service
@RequiredArgsConstructor
public class GuestQuotaService {

    static final String EXHAUSTED_MESSAGE = "오늘 체험 채점이 마감됐어요. 예시 기록은 계속 볼 수 있어요.";
    static final String MOCK_BLOCKED_MESSAGE = "모의고사는 카카오 로그인 후 이용할 수 있어요";

    private final GuestProperties properties;
    private final ScoringJobItemRepository itemRepository;
    private final MemberRepository memberRepository;

    public static boolean isGuest(Member member) {
        return member != null && GuestProperties.PROVIDER.equals(member.getProvider());
    }

    // 오늘 더 채점받을 수 있는 문항 수(개인·전체 중 작은 쪽). 게스트가 아니면 호출하지 않는다
    public int remaining(Member guest) {
        LocalDateTime since = LocalDate.now().atStartOfDay();
        long mine = itemRepository.countByJobMemberIdAndJobCreatedAtGreaterThanEqual(guest.getId(), since);
        long all = itemRepository.countByJobMemberProviderAndJobCreatedAtGreaterThanEqual(GuestProperties.PROVIDER, since);
        long left = Math.min(properties.getDailyQuestions() - mine, properties.getGlobalDailyQuestions() - all);
        return (int) Math.max(0, left);
    }

    // 접수·업로드 URL 발급 직전 검사. 게스트가 아니면 아무것도 하지 않는다
    public void assertCanSubmit(Member member, PracticeMode mode, int questionCount) {
        if (!isGuest(member)) return;
        if (mode == PracticeMode.MOCK_EXAM) {
            throw new IllegalArgumentException(MOCK_BLOCKED_MESSAGE);
        }
        if (remaining(member) < questionCount) {
            throw new RateLimitExceededException(EXHAUSTED_MESSAGE);
        }
    }

    // 연습 시작 화면용: 게스트면 오늘 남은 문항 수와 시작 가능 여부를 모델에 싣는다(question.html이 안내·녹음 비활성화에 쓴다)
    public void addStartAttributes(Long memberId, int questionCount, Model model) {
        if (memberId == null) return;
        Member member = memberRepository.findById(memberId).orElse(null);
        if (!isGuest(member)) return;
        int remaining = remaining(member);
        model.addAttribute("guestRemaining", remaining);
        model.addAttribute("guestBlocked", remaining < questionCount);
    }
}
