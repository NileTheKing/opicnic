package com.opicnic.opicnic.service.attempt;

import com.opicnic.opicnic.domain.Question;
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

    // Question은 정적 데이터 — 앱 수명 동안 캐시 유효
    private final Map<Long, Question> questionCache = new ConcurrentHashMap<>();

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
                    .forEach(q -> questionCache.put(q.getId(), q));
        }

        return targetIds.stream()
                .map(id -> id == null ? selfIntroDto() : QuestionDto.from(requireQuestion(questionCache, id)))
                .toList();
    }

    public void consume(String attemptId) {
        store.markSubmitted(attemptId);
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

    private Question requireQuestion(Map<Long, Question> questionMap, Long questionId) {
        Question question = questionMap.get(questionId);
        if (question == null) {
            throw new IllegalStateException("문제를 찾을 수 없습니다. questionId=" + questionId);
        }
        return question;
    }
}
