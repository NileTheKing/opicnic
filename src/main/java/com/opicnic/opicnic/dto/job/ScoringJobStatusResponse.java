package com.opicnic.opicnic.dto.job;

import com.opicnic.opicnic.domain.job.ScoringJob;
import com.opicnic.opicnic.domain.job.ScoringJobItem;
import com.opicnic.opicnic.domain.job.ScoringJobItemStatus;
import com.opicnic.opicnic.domain.job.ScoringJobStatus;

import java.util.List;

// 폴링 응답. 서버는 아무것도 기억하지 않는다 — 매번 DB를 읽어 이 시점의 상태를 센다.
// 이 응답이 곧 "현재 상태 스냅샷"이라, 나중에 SSE를 얹을 때 재접속 시 첫 이벤트로 그대로 쓴다.
public record ScoringJobStatusResponse(
        String id,
        ScoringJobStatus status,
        int total,
        int done,
        int failed,
        List<Item> items
) {
    // 실패 원문(lastError)은 내보내지 않는다 — 제공자 응답 본문에 조직 ID 같은 내부 정보가 섞여 있다. 원인은 서버 로그·DB에서 본다
    public record Item(int index, ScoringJobItemStatus status, int attempts, Long feedbackResultId) {
    }

    public static ScoringJobStatusResponse from(ScoringJob job) {
        List<Item> items = job.getItems().stream()
                .map(i -> new Item(i.getQuestionIndex(), i.getStatus(), i.getAttempts(), i.getFeedbackResultId()))
                .toList();
        int done = (int) job.getItems().stream().filter(i -> i.getStatus() == ScoringJobItemStatus.DONE).count();
        int failed = (int) job.getItems().stream().filter(i -> i.getStatus() == ScoringJobItemStatus.FAILED).count();
        return new ScoringJobStatusResponse(job.getId(), job.getStatus(), items.size(), done, failed, items);
    }
}
