package com.opicnic.opicnic.service.attempt;

import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.AttemptStatus;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class PracticeAttemptService {

    private final PracticeAttemptStore store;
    private final QuestionRepository questionRepository;

    // Question은 정적 데이터 — 앱 수명 동안 캐시 유효.
    // JPA 엔티티가 아니라 QuestionDto를 캐싱한다: Question.questionSet은 LAZY라, 엔티티를 캐시에
    // 담아두면 그 엔티티를 로드했던 요청의 영속성 컨텍스트가 끝난 뒤(다른 요청이 캐시 히트로 재사용할 때)
    // topic을 읽으려다 LazyInitializationException이 난다. DTO는 이미 topic이 문자열로 풀려있어
    // 영속성 컨텍스트와 무관하게 재사용 가능하다.
    private final Map<Long, QuestionDto> questionCache = new ConcurrentHashMap<>();

    public PracticeAttempt createAttempt(List<QuestionDto> questions, Long memberId, PracticeMode mode,
                                         String comboPatternKey, String comboCategory) {
        String attemptId = UUID.randomUUID().toString();
        List<Long> questionIds = questions.stream().map(QuestionDto::getId).toList();
        PracticeAttempt attempt = new PracticeAttempt(
                attemptId, questionIds, memberId, mode,
                comboPatternKey, comboCategory,
                Instant.now().plus(2, ChronoUnit.HOURS),
                AttemptStatus.IN_PROGRESS
        );
        store.save(attempt);
        return attempt;
    }

    // ScoringJobItem.questionId(null = 자기소개) → QuestionDto. questionCache는 questionId 기준(REVIEW-08 참고).
    // 워커 스레드엔 웹 요청의 OSIV가 없어 QuestionDto.from()의 questionSet(LAZY) 접근이 세션 밖이 된다 — 트랜잭션 필요
    @Transactional(readOnly = true)
    public QuestionDto questionById(Long questionId) {
        if (questionId == null) return selfIntroDto();
        QuestionDto cached = questionCache.get(questionId);
        if (cached != null) return cached;
        questionRepository.findById(questionId).ifPresent(q -> questionCache.put(q.getId(), QuestionDto.from(q)));
        return requireQuestion(questionCache, questionId);
    }

    // REVIEW-01: 동시 submit 요청 중 정확히 하나만 IN_PROGRESS -> FINALIZING 전이에 성공해
    // "잡 생성 권한"을 갖도록 한다. false면 다른 요청이 이미 처리했거나(SUBMITTED) 처리
    // 중이라는(FINALIZING) 뜻이므로 호출자(ScoringJobService.submit)는 기존 잡을 돌려준다.
    public boolean tryStartFinalizing(String attemptId) {
        return store.tryStartFinalizing(attemptId);
    }

    // REVIEW-01: 잡 행 저장이 끝난 뒤 FINALIZING -> SUBMITTED로 확정한다.
    public boolean confirmSubmitted(String attemptId) {
        return store.confirmSubmitted(attemptId);
    }

    // REVIEW-08: 관리자가 QuestionSet을 수정/삭제해도 이 캐시는 questionId 기준이라 CACHE-01의
    // QuestionAssemblyService.evict(topic)로는 안 비워진다 — 이미 캐시에 담긴 QuestionDto는
    // 옛 topic/내용을 계속 서빙한다. 관리 작업은 드물어서 topic 단위로 정교하게 골라내는 대신
    // 전체를 비우는 것으로 충분하다(다음 조회 때 캐시 미스로 다시 채워짐).
    public void evictAllQuestionCache() {
        questionCache.clear();
    }

    public PracticeAttempt requireValidAttempt(String attemptId) {
        PracticeAttempt attempt = store.findById(attemptId)
                .orElseThrow(() -> new IllegalStateException("세션이 만료되었거나 존재하지 않습니다."));
        if (attempt.isExpired()) {
            throw new IllegalStateException("세션이 만료되었습니다.");
        }
        // FINALIZING도 SUBMITTED와 동일하게 막는다 — 잡 생성이 진행 중인 attempt에 URL 발급이 끼어들지 않게.
        if (attempt.status() == AttemptStatus.SUBMITTED || attempt.status() == AttemptStatus.FINALIZING) {
            throw new IllegalStateException("이미 제출된 세션입니다.");
        }
        return attempt;
    }

    private QuestionDto selfIntroDto() {
        return new QuestionDto(
                null,
                "Please introduce yourself. Tell me about who you are, what you do, and anything important about yourself.",
                "자기소개",
                null
        );
    }

    private QuestionDto requireQuestion(Map<Long, QuestionDto> questionMap, Long questionId) {
        QuestionDto question = questionMap.get(questionId);
        if (question == null) {
            throw new IllegalStateException("문제를 찾을 수 없습니다. questionId=" + questionId);
        }
        return question;
    }
}
