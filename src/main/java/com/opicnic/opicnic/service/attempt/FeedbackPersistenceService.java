package com.opicnic.opicnic.service.attempt;

import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.FeedbackTag;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.attempt.PracticeAttempt;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.FeedbackTagDto;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.FeedbackTagRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// DATA-01: 피드백 저장과 태그 저장이 별개 saveAll 호출이라 태그 저장이 실패해도 피드백은
// 이미 커밋된 채로 남는 문제가 있었다. 컨트롤러의 finalize()가 자기 자신을 호출하는 구조에선
// @Transactional이 프록시를 안 거쳐 무시되므로, 별도 빈으로 분리해 두 저장을 하나의
// 트랜잭션으로 묶는다 — 하나가 실패하면 둘 다 롤백된다.
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackPersistenceService {

    private final MemberRepository memberRepository;
    private final FeedbackResultRepository feedbackResultRepository;
    private final FeedbackTagRepository feedbackTagRepository;

    @Transactional
    public void saveFeedbackResults(List<FeedbackDTO> feedbackResults, OAuth2User oAuth2User,
                                     PracticeAttempt attempt) {
        if (oAuth2User == null) return;
        String provider = oAuth2User.getAttribute("provider");
        String providerId = oAuth2User.getAttribute("providerId");
        Member member = memberRepository.findByProviderAndProviderId(provider, providerId).orElse(null);
        if (member == null) return;

        // 자기소개는 실제 시험에서도 채점 문항으로 취급되지 않는다 — DB에 아예 저장하지 않아야
        // "총 문항 수", "최근 기록", "코칭 열람 조건" 같은 문항 개수 기반 통계에 섞이지 않는다.
        List<FeedbackDTO> validFeedback = feedbackResults.stream()
                .filter(fb -> !fb.isFailed())
                .filter(fb -> fb.getQuestion().getQuestionType() != null)
                .toList();
        List<FeedbackResult> toSave = validFeedback.stream()
                .map(fb -> FeedbackResult.builder()
                        .member(member)
                        .attemptId(attempt.attemptId())
                        .questionId(fb.getQuestion().getId())
                        .questionType(fb.getQuestion().getQuestionType())
                        .surveyTopicName(fb.getQuestion().getSurveyTopicName())
                        .comboPatternKey(attempt.comboPatternKey())
                        .comboCategory(attempt.comboCategory())
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
                        .build())
                .toList();

        List<FeedbackResult> saved = feedbackResultRepository.saveAll(toSave);

        List<FeedbackTag> tagsToSave = new ArrayList<>();
        for (int i = 0; i < saved.size(); i++) {
            List<FeedbackTagDto> tags = validFeedback.get(i).getTags();
            if (tags == null) continue;
            for (var t : tags) {
                tagsToSave.add(FeedbackTag.builder()
                        .feedbackResult(saved.get(i))
                        .category(t.category())
                        .tag(t.tag())
                        .build());
            }
        }
        feedbackTagRepository.saveAll(tagsToSave);

        log.info("[DB 저장] 피드백 {}건, 태그 {}건 (member: {}, combo: {})",
                saved.size(), tagsToSave.size(), member.getId(), attempt.comboCategory());
    }
}
