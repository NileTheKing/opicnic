package com.opicnic.opicnic.service.attempt;

import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.FeedbackTag;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.job.ScoringJob;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.FeedbackTagDto;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.FeedbackTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// DATA-01: 피드백 저장과 태그 저장이 별개 호출이라 태그 저장이 실패해도 피드백은 이미 커밋된 채로
// 남는 문제가 있었다. 별도 빈으로 분리해 두 저장을 하나의 트랜잭션으로 묶는다 — 하나가 실패하면 둘 다 롤백된다.
// 워커는 이 트랜잭션을 자기 트랜잭션(문항 DONE + 잡 마무리) 안에서 부르므로 결과 행·태그·문항 상태가 함께 커밋된다.
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackPersistenceService {

    private final FeedbackResultRepository feedbackResultRepository;
    private final FeedbackTagRepository feedbackTagRepository;

    // 워커용: 문항 하나를 태그까지 한 트랜잭션으로 저장하고 저장된 엔티티를 돌려준다.
    // 문항 상태(DONE)와 결과 행이 같은 DB에 있어 워커가 "상태 갱신 + 결과 저장"을 한 트랜잭션에 묶을 수 있다 —
    // 큐를 DB로 두는 진짜 이득(불일치 없음). 자기소개(questionType null)는 저장하지 않고 null을 돌려준다.
    // 자기소개는 실제 시험에서도 채점 문항으로 취급되지 않는다 — DB에 아예 저장하지 않아야
    // "총 문항 수", "최근 기록", "코칭 열람 조건" 같은 문항 개수 기반 통계에 섞이지 않는다.
    @Transactional
    public FeedbackResult saveOne(FeedbackDTO fb, ScoringJob job) {
        if (fb.isFailed() || fb.getQuestion().getQuestionType() == null) return null;
        FeedbackResult saved = feedbackResultRepository.save(
                toEntity(fb, job.getMember(), job.getId(), job.getComboPatternKey(), job.getComboCategory()));
        List<FeedbackTag> tags = new ArrayList<>();
        if (fb.getTags() != null) {
            for (var t : fb.getTags()) {
                tags.add(FeedbackTag.builder().feedbackResult(saved).category(t.category()).tag(t.tag()).build());
            }
        }
        feedbackTagRepository.saveAll(tags);
        return saved;
    }

    private static FeedbackResult toEntity(FeedbackDTO fb, Member member, String attemptId,
                                           String comboPatternKey, String comboCategory) {
        return FeedbackResult.builder()
                .member(member)
                .attemptId(attemptId)
                .questionId(fb.getQuestion().getId())
                .questionType(fb.getQuestion().getQuestionType())
                .surveyTopicName(fb.getQuestion().getSurveyTopicName())
                .comboPatternKey(comboPatternKey)
                .comboCategory(comboCategory)
                .questionContent(fb.getQuestion().getContent())
                .sttText(fb.getSttText())
                .expression(fb.getExpression())
                .expressionScore(fb.getExpressionScore())
                .expressionQuote(fb.getExpressionQuote())
                .expressionFix(fb.getExpressionFix())
                .accuracy(fb.getAccuracy())
                .accuracyScore(fb.getAccuracyScore())
                .accuracyQuote(fb.getAccuracyQuote())
                .accuracyFix(fb.getAccuracyFix())
                .mainPoint(fb.getMainPoint())
                .mainPointScore(fb.getMainPointScore())
                .mainPointQuote(fb.getMainPointQuote())
                .mainPointFix(fb.getMainPointFix())
                .fluency(fb.getFluency())
                .fluencyScore(fb.getFluencyScore())
                .content(fb.getContent())
                .contentScore(fb.getContentScore())
                .contentQuote(fb.getContentQuote())
                .contentFix(fb.getContentFix())
                .overall(fb.getOverall())
                .overallGrade(fb.getOverallGrade())
                .improvements(fb.getImprovements())
                .modelAnswer(fb.getModelAnswer())
                .modelAnswerComment(fb.getModelAnswerComment())
                .build();
    }
}
