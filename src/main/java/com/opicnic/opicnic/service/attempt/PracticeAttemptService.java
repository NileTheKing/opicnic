package com.opicnic.opicnic.service.attempt;

import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.domain.enums.AttemptStatus;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

    // 특정 인덱스의 문제만 복원 (submit/retry 시 해당 인덱스만 처리)
    // @Transactional 제거 — 캐시 히트 시 SQL 없음. 캐시 미스 시 findAllById가 자체 트랜잭션 사용
    public List<QuestionDto> restoreQuestionsForIndexes(String attemptId, List<Integer> indexes) {
        PracticeAttempt attempt = requireValidAttempt(attemptId);
        List<Long> allIds = attempt.questionIds();
        if (indexes.stream().anyMatch(index -> index < 0 || index >= allIds.size())) {
            throw new IllegalArgumentException("문제 index가 유효하지 않습니다.");
        }

        List<Long> targetIds = indexes.stream()
                .map(allIds::get)
                .toList();

        List<Long> cacheMissIds = targetIds.stream()
                .filter(id -> id != null && !questionCache.containsKey(id))
                .distinct()
                .toList();
        if (!cacheMissIds.isEmpty()) {
            questionRepository.findAllById(cacheMissIds)
                    .forEach(q -> questionCache.put(q.getId(), QuestionDto.from(q)));
        }

        return targetIds.stream()
                .map(id -> id == null ? selfIntroDto() : requireQuestion(questionCache, id))
                .toList();
    }

    // REVIEW-01: 동시 finalize 요청 중 정확히 하나만 IN_PROGRESS -> FINALIZING 전이에 성공해
    // "DB 저장 권한"을 갖도록 한다. false면 다른 요청이 이미 처리했거나(SUBMITTED) 처리
    // 중이라는(FINALIZING) 뜻이므로 호출자는 DB 저장을 진행하면 안 된다.
    public boolean tryStartFinalizing(String attemptId) {
        return store.tryStartFinalizing(attemptId);
    }

    // REVIEW-01: DB 저장이 성공적으로 끝난 뒤 FINALIZING -> SUBMITTED로 확정한다.
    public boolean confirmSubmitted(String attemptId) {
        return store.confirmSubmitted(attemptId);
    }

    // REVIEW-01: DB 저장이 실패하면 FINALIZING에 영구히 멈춰있지 않도록 IN_PROGRESS로 되돌려
    // 사용자가 finalize를 다시 시도할 수 있게 복구한다.
    public boolean revertFinalizing(String attemptId) {
        return store.revertToInProgress(attemptId);
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
        // FINALIZING도 SUBMITTED와 동일하게 막는다 — DB 저장이 진행 중인 attempt에 새 답변
        // 제출/재시도가 끼어들면 세션 결과 map을 finalize 스레드와 동시에 건드리게 된다.
        if (attempt.status() == AttemptStatus.SUBMITTED || attempt.status() == AttemptStatus.FINALIZING) {
            throw new IllegalStateException("이미 제출된 세션입니다.");
        }
        return attempt;
    }

    // REVIEW-01: finalize 전용 조회. submitAnswers/retry와 달리 SUBMITTED를 예외로 막지 않는다 —
    // 이미 완료된 attempt에 대한 재요청을 컨트롤러가 멱등하게(같은 성공 응답) 처리할 수 있어야
    // 하므로, 상태에 따른 분기는 여기서 던지지 않고 호출자(컨트롤러)에게 맡긴다.
    public PracticeAttempt requireAttemptForFinalize(String attemptId) {
        PracticeAttempt attempt = store.findById(attemptId)
                .orElseThrow(() -> new IllegalStateException("세션이 만료되었거나 존재하지 않습니다."));
        if (attempt.isExpired()) {
            throw new IllegalStateException("세션이 만료되었습니다.");
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
