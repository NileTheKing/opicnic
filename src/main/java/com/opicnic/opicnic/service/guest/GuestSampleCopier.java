package com.opicnic.opicnic.service.guest;

import com.opicnic.opicnic.domain.CoachingReport;
import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.FeedbackTag;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.repository.CoachingReportRepository;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.FeedbackTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// 게스트 화면(학습분석·코칭·기록)이 비어 보이지 않게 예시 기록(GuestSample)을 게스트 소유로 복사한다.
// 호출자(GuestService)의 트랜잭션 안에서 돈다.
@Component
@RequiredArgsConstructor
@Slf4j
public class GuestSampleCopier {

    private final FeedbackResultRepository feedbackResultRepository;
    private final FeedbackTagRepository feedbackTagRepository;
    private final CoachingReportRepository coachingReportRepository;

    public void copy(GuestSample sample, Member guest, LocalDateTime now) {
        List<GuestSample.Result> sources = sample.results() == null ? List.of() : sample.results();
        // 가장 최근 기록이 "지금"이 되도록 통째로 밀어서 간격(콤보↔유형 사이클, 구간 분석)을 유지한다
        LocalDateTime newest = sources.stream().map(GuestSample.Result::createdAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        Duration shift = newest == null ? Duration.ZERO : Duration.between(newest, now);

        // 같은 콤보로 묶인 기록은 새 attemptId도 하나로 묶는다 — 게스트마다 달라야 잡 id와 겹치지 않는다
        Map<String, String> attemptIds = new HashMap<>();
        List<FeedbackTag> tags = new ArrayList<>();
        for (GuestSample.Result src : sources) {
            String attemptId = src.attemptId() == null ? null
                    : attemptIds.computeIfAbsent(src.attemptId(), k -> UUID.randomUUID().toString());
            FeedbackResult copy = feedbackResultRepository.save(src.toEntity(guest, attemptId));
            // @CreationTimestamp가 insert 시각으로 덮어쓰므로 저장 후 시각을 따로 맞춘다
            if (src.createdAt() != null) {
                feedbackResultRepository.updateCreatedAt(copy.getId(), src.createdAt().plus(shift));
            }
            if (src.tags() != null) {
                for (GuestSample.Tag t : src.tags()) {
                    tags.add(FeedbackTag.builder().feedbackResult(copy).category(t.category()).tag(t.tag()).build());
                }
            }
        }
        if (!tags.isEmpty()) feedbackTagRepository.saveAll(tags);

        if (sample.coaching() != null) {
            CoachingReport copy = coachingReportRepository.save(CoachingReport.builder()
                    .member(guest).content(sample.coaching().content()).basedOnCount(sample.coaching().basedOnCount()).build());
            coachingReportRepository.updateCreatedAt(copy.getId(), now);
        }
        log.info("[Guest] 예시 기록 복사 guest={} results={}", guest.getId(), sources.size());
    }
}
