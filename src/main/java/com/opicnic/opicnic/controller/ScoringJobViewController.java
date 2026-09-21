package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.job.ScoringJob;
import com.opicnic.opicnic.domain.job.ScoringJobItem;
import com.opicnic.opicnic.domain.job.ScoringJobItemStatus;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import com.opicnic.opicnic.service.job.ScoringJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 비동기 모의고사 결과 화면. 처리 중이면 진행 페이지(2초 폴링, 끝나면 같은 URL 새로고침),
// 끝났으면 DB의 FeedbackResult를 기존 feedback.html로 그린다 — 결과 템플릿은 동기 경로와 하나.
// 세션에 의존하지 않으므로 탭을 닫았다 와도, 서버가 재시작돼도 같은 URL로 결과를 본다.
@Controller
@RequiredArgsConstructor
public class ScoringJobViewController {

    private final ScoringJobService scoringJobService;
    private final FeedbackResultRepository feedbackResultRepository;
    private final PracticeAttemptService attemptService;
    private final MemberRepository memberRepository;

    @GetMapping("/practice/mock/result/{jobId}")
    @Transactional(readOnly = true)
    public String result(@PathVariable String jobId, @AuthenticationPrincipal OAuth2User user, Model model) {
        ScoringJob job = scoringJobService.find(jobId).orElse(null);
        if (job == null || !isOwner(job, user)) return "redirect:/";

        if (!job.isFinished()) {
            model.addAttribute("jobId", jobId);
            model.addAttribute("total", job.getItems().size());
            return "practice/mock-progress";
        }

        Map<Long, FeedbackResult> results = feedbackResultRepository.findAllByAttemptId(jobId).stream()
                .collect(Collectors.toMap(FeedbackResult::getId, Function.identity()));
        List<FeedbackDTO> feedbackResults = new ArrayList<>();
        for (ScoringJobItem item : job.getItems()) {
            QuestionDto question = attemptService.questionById(item.getQuestionId());
            if (item.getStatus() == ScoringJobItemStatus.FAILED) {
                feedbackResults.add(FeedbackDTO.builder().question(question).failed(true).errorMessage(item.getLastError()).build());
            } else if (item.getFeedbackResultId() == null) {
                // 자기소개: 채점·저장 안 함. 동기 경로의 selfIntroductionDto와 같은 문구
                feedbackResults.add(FeedbackDTO.builder().question(question).sttText(item.getSttText())
                        .overall("자기소개는 채점 대상이 아닙니다. 수고하셨어요!").build());
            } else {
                FeedbackResult r = results.get(item.getFeedbackResultId());
                feedbackResults.add(r == null
                        ? FeedbackDTO.builder().question(question).failed(true).errorMessage("결과 없음").build()
                        : FeedbackDTO.from(r));
            }
        }
        model.addAttribute("feedbackResults", feedbackResults);
        return "practice/feedback";
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
