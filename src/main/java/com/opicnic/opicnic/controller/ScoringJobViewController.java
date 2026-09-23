package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.job.ScoringJob;
import com.opicnic.opicnic.domain.job.ScoringJobItem;
import com.opicnic.opicnic.domain.job.ScoringJobItem.FailureReason;
import com.opicnic.opicnic.domain.job.ScoringJobItemStatus;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import com.opicnic.opicnic.service.job.ScoringJobService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 비동기 채점 결과 화면(모의고사·콤보 공용). 끝난 문항부터 카드가 채워진다 — 한 문항이 늦어도 나머지를 먼저 읽는다.
// 처리 중이면 화면이 2초마다 상태를 묻고, 새로 끝난 문항만 카드 조각(/cards/{index})으로 받아 끼운다.
// 30초가 지나도 남은 문항이 있으면 "완료되면 홈에서 알려드릴게요"로 떠나도 된다고 알린다(서버는 30분까지 재시도).
// 세션에 의존하지 않으므로 탭을 닫았다 와도, 서버가 재시작돼도 같은 URL로 결과를 본다.
// 트랜잭션은 걸지 않는다 — 읽기는 OSIV로, "결과를 봤다" 기록은 서비스의 쓰기 트랜잭션으로.
@Controller
@RequiredArgsConstructor
public class ScoringJobViewController {

    private final ScoringJobService scoringJobService;
    private final FeedbackResultRepository feedbackResultRepository;
    private final PracticeAttemptService attemptService;
    private final MemberRepository memberRepository;

    @GetMapping("/practice/result/{jobId}")
    public String result(@PathVariable String jobId, @AuthenticationPrincipal OAuth2User user, Model model) {
        ScoringJob job = scoringJobService.find(jobId).orElse(null);
        if (job == null || !isOwner(job, user)) return "redirect:/";
        if (job.isFinished()) scoringJobService.markResultSeen(jobId);

        Map<Long, FeedbackResult> results = feedbackResultRepository.findAllByAttemptId(jobId).stream()
                .collect(Collectors.toMap(FeedbackResult::getId, Function.identity()));
        List<ResultCard> cards = job.getItems().stream().map(item -> toCard(item, results)).toList();

        model.addAttribute("jobId", jobId);
        model.addAttribute("mode", job.getMode());
        model.addAttribute("finished", job.isFinished());
        // 접수 후 경과 — 서버 시계로 재서 넘긴다(브라우저 시계와 어긋나도 "30초 뒤 안내"가 맞게)
        model.addAttribute("elapsedMillis", Duration.between(job.getCreatedAt(), LocalDateTime.now()).toMillis());
        model.addAttribute("cards", cards);
        return "practice/feedback";
    }

    // 처리 중 화면이 새로 끝난 문항 하나를 받아 끼우는 조각. 잡이 이걸로 끝났으면 "봤다"도 여기서 남긴다
    // (마지막 문항 카드를 받는 순간 = 사용자가 화면에서 결과를 다 본 순간)
    @GetMapping("/practice/result/{jobId}/cards/{index}")
    public String card(@PathVariable String jobId, @PathVariable int index, @AuthenticationPrincipal OAuth2User user, Model model) {
        ScoringJob job = scoringJobService.find(jobId).orElse(null);
        if (job == null || !isOwner(job, user)) return "redirect:/";
        if (job.isFinished()) scoringJobService.markResultSeen(jobId);
        ScoringJobItem item = job.getItems().stream().filter(i -> i.getQuestionIndex() == index).findFirst().orElse(null);
        if (item == null) return "redirect:/";

        Map<Long, FeedbackResult> results = item.getFeedbackResultId() == null ? Map.of()
                : feedbackResultRepository.findById(item.getFeedbackResultId())
                        .map(r -> Map.of(r.getId(), r)).orElse(Map.of());
        model.addAttribute("card", toCard(item, results));
        return "practice/feedback :: card";
    }

    private ResultCard toCard(ScoringJobItem item, Map<Long, FeedbackResult> results) {
        QuestionDto question = attemptService.questionById(item.getQuestionId());
        int number = item.getQuestionIndex() + 1;
        if (!item.isFinished()) {
            return new ResultCard(item.getQuestionIndex(), number, "PENDING",
                    FeedbackDTO.builder().question(question).build(), null);
        }
        if (item.getStatus() == ScoringJobItemStatus.FAILED) {
            return new ResultCard(item.getQuestionIndex(), number, "FAILED",
                    FeedbackDTO.builder().question(question).failed(true).errorMessage(item.getLastError()).build(),
                    failMessage(item.getFailureReason()));
        }
        if (item.getFeedbackResultId() == null) {
            // 자기소개: 채점·저장 안 함
            return new ResultCard(item.getQuestionIndex(), number, "DONE", FeedbackDTO.builder().question(question)
                    .sttText(item.getSttText()).overall("자기소개는 채점 대상이 아닙니다. 수고하셨어요!").build(), null);
        }
        FeedbackResult r = results.get(item.getFeedbackResultId());
        return r == null
                ? new ResultCard(item.getQuestionIndex(), number, "FAILED",
                        FeedbackDTO.builder().question(question).failed(true).build(), failMessage(null))
                : new ResultCard(item.getQuestionIndex(), number, "DONE", FeedbackDTO.from(r), null);
    }

    // 할 수 없는 일("다시 시도해 주세요")을 시키지 않는다 — 왜 안 됐는지만 말한다
    private static String failMessage(FailureReason reason) {
        if (reason == null) return "이 답변은 분석하지 못했어요.";
        return switch (reason) {
            case AUDIO -> "녹음 파일에 문제가 있어 이 답변은 분석할 수 없었어요.";
            case INVALID_OUTPUT -> "이 답변의 채점 결과를 만들지 못했어요.";
            case RETRY_BUDGET_EXCEEDED -> "채점 서버 장애가 길어져 이 답변은 분석하지 못했어요.";
        };
    }

    @Getter
    @AllArgsConstructor
    public static class ResultCard {
        private final int index;
        private final int number;
        private final String state;         // DONE / FAILED / PENDING
        private final FeedbackDTO feedback;
        private final String failMessage;
    }

    // ScoringJobApiController.rejectIfNotOwner와 같은 규칙: dev 테스터 회원의 잡은 누구나
    private boolean isOwner(ScoringJob job, OAuth2User user) {
        Member owner = job.getMember();
        if ("dev".equals(owner.getProvider())) return true;
        if (user == null) return false;
        return memberRepository.findByProviderAndProviderId(user.getAttribute("provider"), user.getAttribute("providerId"))
                .map(m -> m.getId().equals(owner.getId())).orElse(false);
    }
}
