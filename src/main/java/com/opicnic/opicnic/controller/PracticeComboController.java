// src/main/java/com/opicnic/opicnic/controller/PracticeComboController.java

package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto; // QuestionDto 임포트
// import com.opicnic.opicnic.domain.Question; // 더 이상 이 엔티티는 직접 사용하지 않음
import com.opicnic.opicnic.dto.QuestionWrapperDTO; // QuestionWrapperDTO도 QuestionDto를 포함하도록 변경 필요
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.ComboPracticeService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/practice")
@RequiredArgsConstructor
@Slf4j
class PracticeComboController {

    private final ComboPracticeService practiceService;
    private final FeedbackService feedbackService;

    @GetMapping("/combo")
    public String startComboPractice(
            @RequestParam("topic") String topic,
            @RequestParam("difficulty") String difficulty,
            Model model,
            HttpSession session) {

        // ComboPracticeService에서 List<QuestionDto>를 반환하도록 변경했으므로, 여기도 QuestionDto로 받습니다.
        List<QuestionDto> questions = practiceService.getComboQuestions(topic, difficulty);
        model.addAttribute("questions", questions);

        session.setAttribute("practiceQuestions", questions);

        return "/practice/question";
    }

    @PostMapping("/combo/feedback")
    public String submitComboAnswers (
            @RequestParam("files") List<MultipartFile> files,
            @ModelAttribute QuestionWrapperDTO questionWrapperDto,
            Model model
    ) throws IOException {
        // 이 부분이 중요합니다: QuestionWrapperDTO가 QuestionDto 리스트를 받도록 수정해야 합니다.
        // 예를 들어 QuestionWrapperDTO 내부에 List<QuestionDto> 필드가 있어야 합니다.
        // 현재는 questionWrapperDto.getQuestions()가 List<Question>을 반환할 텐데,
        // 클라이언트에서 'questions[index].id', 'questions[index].content', 'questions[index].topic', 'questions[index].difficulty'와 같은
        // QuestionDto 필드 이름으로 데이터를 보내고 있기 때문에 매핑 문제가 발생할 수 있습니다.
        // 따라서 QuestionWrapperDTO 내부도 QuestionDto 타입으로 변경해야 합니다.
        // List<Question> questions = questionWrapperDto.getQuestions(); // 이 부분은 이제 QuestionDto 리스트를 사용해야 합니다.

        // 임시 방편으로, 만약 QuestionWrapperDTO 수정이 어렵다면,
        // session.getAttribute("practiceQuestions")를 사용하여 QuestionDto 리스트를 가져오거나,
        // FeedbackDTO에 QuestionDto 정보도 포함하도록 수정할 수 있습니다.
        // 여기서는 QuestionWrapperDTO가 QuestionDto를 처리한다고 가정합니다.
        List<QuestionDto> questionsFromClient = questionWrapperDto.getQuestions(); // 가정: QuestionWrapperDTO에 List<QuestionDto> getter가 있음

        List<FeedbackDTO> feedbackList = feedbackService.getComboFeedbackParallel(files, questionsFromClient);
        model.addAttribute("feedbackList", feedbackList);
        return "/practice/feedback";
    }
}