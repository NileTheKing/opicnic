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

    // DATA-01: 동시 finalize 요청 중 정확히 하나만 이 요청이 "제출 처리 권한"을 갖도록
    // 원자적으로 전이한다. false면 다른 요청이 이미 처리했거나 처리 중이라는 뜻이므로
    // 호출자는 DB 저장을 진행하면 안 된다.
    public boolean tryConsume(String attemptId) {
        return store.tryMarkSubmitted(attemptId);
    }

    public PracticeAttempt requireValidAttempt(String attemptId) {
        PracticeAttempt attempt = store.findById(attemptId)
                .orElseThrow(() -> new IllegalStateException("세션이 만료되었거나 존재하지 않습니다."));
        if (attempt.isExpired()) {
            throw new IllegalStateException("세션이 만료되었습니다.");
        }
        if (attempt.status() == AttemptStatus.SUBMITTED) {
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
