package com.opicnic.opicnic.service.guest;

import com.opicnic.opicnic.domain.CoachingReport;
import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.FeedbackTag;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.SurveyProfile;
import com.opicnic.opicnic.repository.CoachingReportRepository;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.FeedbackTagRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 게스트 화면(학습분석·코칭·기록)이 비어 보이지 않게, 지정한 회원의 기록을 복사해 준다.
// 원본 회원 id는 설정으로만 받는다 — 코드에 박지 않는다. 호출자(GuestService)의 트랜잭션 안에서 돈다.
@Component
@RequiredArgsConstructor
@Slf4j
public class GuestSampleCopier {

    private final MemberRepository memberRepository;
    private final SurveyProfileRepository surveyProfileRepository;
    private final FeedbackResultRepository feedbackResultRepository;
    private final FeedbackTagRepository feedbackTagRepository;
    private final CoachingReportRepository coachingReportRepository;

    public void copy(Long sampleMemberId, Member guest, LocalDateTime now) {
        if (memberRepository.findById(sampleMemberId).isEmpty()) {
            log.warn("[Guest] 예시 원본 회원이 없어 빈 상태로 시작합니다. sampleMemberId={}", sampleMemberId);
            return;
        }
        copySurveyProfile(sampleMemberId, guest);

        List<FeedbackResult> sources = feedbackResultRepository.findByMemberIdOrderByCreatedAtDesc(sampleMemberId);
        // 가장 최근 기록이 "지금"이 되도록 통째로 밀어서 간격(콤보↔유형 사이클, 구간 분석)을 유지한다
        Duration shift = sources.isEmpty() || sources.get(0).getCreatedAt() == null
                ? Duration.ZERO : Duration.between(sources.get(0).getCreatedAt(), now);

        // 같은 콤보로 묶인 기록은 새 attemptId도 하나로 묶는다. 원본 id를 그대로 쓰면 원본 잡 id와 겹친다
        Map<String, String> attemptIds = new HashMap<>();
        Map<Long, FeedbackResult> copiedBySourceId = new HashMap<>();
        for (FeedbackResult src : sources) {
            String attemptId = src.getAttemptId() == null ? null
                    : attemptIds.computeIfAbsent(src.getAttemptId(), k -> UUID.randomUUID().toString());
            FeedbackResult copy = feedbackResultRepository.save(copyOf(src, guest, attemptId));
            if (src.getCreatedAt() != null) {
                feedbackResultRepository.updateCreatedAt(copy.getId(), src.getCreatedAt().plus(shift));
            }
            copiedBySourceId.put(src.getId(), copy);
        }

        if (!copiedBySourceId.isEmpty()) {
            List<FeedbackTag> tags = new ArrayList<>();
            for (FeedbackTag t : feedbackTagRepository.findByFeedbackResultIdIn(new ArrayList<>(copiedBySourceId.keySet()))) {
                tags.add(FeedbackTag.builder()
                        .feedbackResult(copiedBySourceId.get(t.getFeedbackResult().getId()))
                        .category(t.getCategory()).tag(t.getTag()).build());
            }
            feedbackTagRepository.saveAll(tags);
        }

        coachingReportRepository.findTopByMemberIdOrderByCreatedAtDesc(sampleMemberId).ifPresent(r -> {
            CoachingReport copy = coachingReportRepository.save(CoachingReport.builder()
                    .member(guest).content(r.getContent()).basedOnCount(r.getBasedOnCount()).build());
            coachingReportRepository.updateCreatedAt(copy.getId(), now);
        });
        log.info("[Guest] 예시 기록 복사 guest={} results={}", guest.getId(), copiedBySourceId.size());
    }

    // 배경설문이 없으면 게스트가 첫 화면에서 온보딩으로 튕긴다 — 예시 회원의 설문도 같이 준다
    private void copySurveyProfile(Long sampleMemberId, Member guest) {
        surveyProfileRepository.findByMemberId(sampleMemberId).ifPresent(p ->
                surveyProfileRepository.save(SurveyProfile.builder()
                        .member(guest)
                        .occupationType(p.getOccupationType())
                        .residenceType(p.getResidenceType())
                        .targetGrade(p.getTargetGrade())
                        .preferredDifficulty(p.getPreferredDifficulty())
                        .selectedTopics(new ArrayList<>(p.getSelectedTopics()))
                        .build()));
    }

    private FeedbackResult copyOf(FeedbackResult s, Member guest, String attemptId) {
        return FeedbackResult.builder()
                .member(guest)
                .questionId(s.getQuestionId())
                .attemptId(attemptId)
                .questionType(s.getQuestionType())
                .surveyTopicName(s.getSurveyTopicName())
                .comboPatternKey(s.getComboPatternKey())
                .comboCategory(s.getComboCategory())
                .questionContent(s.getQuestionContent())
                .sttText(s.getSttText())
                .expression(s.getExpression()).accuracy(s.getAccuracy()).mainPoint(s.getMainPoint())
                .mainPointQuote(s.getMainPointQuote()).mainPointFix(s.getMainPointFix())
                .expressionQuote(s.getExpressionQuote()).expressionFix(s.getExpressionFix())
                .accuracyQuote(s.getAccuracyQuote()).accuracyFix(s.getAccuracyFix())
                .fluency(s.getFluency())
                .content(s.getContent()).contentQuote(s.getContentQuote()).contentFix(s.getContentFix())
                .overall(s.getOverall()).overallGrade(s.getOverallGrade())
                .expressionScore(s.getExpressionScore()).accuracyScore(s.getAccuracyScore())
                .mainPointScore(s.getMainPointScore()).fluencyScore(s.getFluencyScore())
                .contentScore(s.getContentScore())
                .improvements(s.getImprovements())
                .modelAnswer(s.getModelAnswer()).modelAnswerComment(s.getModelAnswerComment())
                .sample(true)
                .build();
    }
}
