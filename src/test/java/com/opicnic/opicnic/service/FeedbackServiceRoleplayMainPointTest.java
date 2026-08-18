package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.QuestionDto;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// SCORE-02 회귀 테스트: TYPE_5~7(롤플레이)의 "MP 평가 제외" 0점이 다른 4개 점수와 함께
// 평균에 그대로 섞여 등급을 구조적으로 낮추던 문제. mainPointScore는 null로 저장되어
// computeGrade/computeOverallText의 평균·최약점 판정에서 제외되어야 한다.
class FeedbackServiceRoleplayMainPointTest {

    @Test
    void roleplayMainPointScoreIsExcludedFromGradeAndOverallText() {
        ComboPracticeService comboPracticeService = Mockito.mock(ComboPracticeService.class);
        STTService sttService = Mockito.mock(STTService.class);
        GroqService groqService = Mockito.mock(GroqService.class);
        FeedbackService feedbackService = new FeedbackService(comboPracticeService, sttService, groqService, new ObjectMapper());

        // fluencyScore가 4점(90단어 이상)이 나오도록 충분히 긴 답변을 준다 — 짧으면 fluency 자체가
        // 평균을 끌어내려 이 테스트가 검증하려는 MP 제외 효과와 뒤섞인다.
        String longAnswer = "Could you tell me what time the store opens on weekends and if it is open on holidays too. " +
                "I also want to know whether I need to make a reservation in advance or if walk ins are fine. " +
                "And one more thing, do you offer any discount for students or for people who visit more than once a month. " +
                "I would really appreciate it if you could give me some details about the return policy as well, " +
                "and please also let me know if there is a parking lot nearby that customers can use for free while shopping.";
        when(sttService.sendStreamToStt(any(), any())).thenReturn(longAnswer);

        // MP=0(평가 제외), 나머지 4개=4 -> 제외 시 평균 4.0(IH 이상), 포함 시 평균 3.2(IM3)
        Map<String, Object> mockFeedback = Map.of(
                "mainPoint", "롤플레이 유형 — MP 평가 제외", "mainPointScore", 0,
                "expression", "표현 양호", "expressionScore", 4,
                "accuracy", "문법 양호", "accuracyScore", 4,
                "content", "내용 양호", "contentScore", 4
        );
        when(groqService.getOpicFeedback(any(), any())).thenReturn(mockFeedback);
        when(groqService.extractFeedbackTags(any(), any(), any(), any(), any()))
                .thenReturn("{\"mainPoint\":[],\"expression\":{\"vocab\":[],\"sentence\":[],\"imagery\":[]},\"accuracy\":[],\"content\":[]}");

        QuestionDto roleplayQuestion = new QuestionDto(1L, "content", "topic", QuestionType.TYPE_6);
        List<InputStream> streams = List.of(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        List<FeedbackDTO> results = feedbackService.getComboFeedbackStreaming(streams, List.of(roleplayQuestion));

        FeedbackDTO result = results.get(0);
        assertThat(result.getMainPointScore()).isNull();
        assertThat(result.getOverall()).doesNotContain("핵심전달");
        assertThat(result.getOverallGrade()).isIn("IH", "AL");
    }
}
