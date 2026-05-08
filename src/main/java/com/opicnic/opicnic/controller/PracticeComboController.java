package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.dto.QuestionWrapperDTO;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.service.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Controller
@RequestMapping("/practice/combo")
@RequiredArgsConstructor
@Slf4j
public class PracticeComboController {

    private final FeedbackService feedbackService;
    private final FeedbackResultRepository feedbackResultRepository;
    private final MemberRepository memberRepository;

    @GetMapping
    public String startComboPractice(
            @RequestParam String topic,
            @RequestParam String difficulty,
            Model model) {
        log.info("콤보 연습 시작: topic={}, difficulty={}", topic, difficulty);
        List<QuestionDto> questions = feedbackService.getComboQuestions(topic, difficulty);
        model.addAttribute("questions", questions);
        return "/practice/question";
    }

    @PostMapping("/feedback")
    public String submitComboAnswers(HttpServletRequest request,
                                     @ModelAttribute QuestionWrapperDTO questionWrapper,
                                     @AuthenticationPrincipal OAuth2User oAuth2User,
                                     Model model) {
        log.info("[최종 최적화] 피드백 요청 수신");

        try {
            Collection<Part> parts = request.getParts();
            List<InputStream> inputStreams = new ArrayList<>();
            for (Part part : parts) {
                if (part.getName().equals("files")) {
                    inputStreams.add(part.getInputStream());
                }
            }

            List<QuestionDto> questionsFromClient = questionWrapper.getQuestions();
            List<FeedbackDTO> feedbackList = feedbackService.getComboFeedbackStreaming(inputStreams, questionsFromClient);

            saveFeedbackResults(feedbackList, oAuth2User);

            model.addAttribute("feedbackList", feedbackList);
            return "/practice/feedback";

        } catch (Exception e) {
            log.error("처리 중 오류 발생: {}", e.getMessage(), e);
            return "error";
        }
    }

    private void saveFeedbackResults(List<FeedbackDTO> feedbackList, OAuth2User oAuth2User) {
        if (oAuth2User == null) return;

        String providerId = oAuth2User.getAttribute("providerId");
        String provider = oAuth2User.getAttribute("provider");
        Member member = memberRepository.findByProviderAndProviderId(provider, providerId).orElse(null);
        if (member == null) return;

        List<FeedbackResult> results = feedbackList.stream()
                .filter(fb -> !fb.isFailed())
                .map(fb -> FeedbackResult.builder()
                        .member(member)
                        .questionContent(fb.getQuestion().getContent())
                        .sttText(fb.getSttText())
                        .vocabulary(fb.getVocabulary())
                        .grammar(fb.getGrammar())
                        .mainPoint(fb.getMainPoint())
                        .fluency(fb.getFluency())
                        .content(fb.getContent())
                        .overall(fb.getOverall())
                        .improvements(fb.getImprovements())
                        .build())
                .toList();

        feedbackResultRepository.saveAll(results);
        log.info("[DB 저장] 피드백 {}건 저장 완료 (member: {})", results.size(), member.getId());
    }
}
