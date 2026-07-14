package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.domain.CoachingReport;
import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.FeedbackTag;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.dto.QuestionDto;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.FeedbackTagRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 로컬 DB 코칭 화면 수동 검증용 1회성 시더. 실제 GroqService(getOpicFeedback+extractFeedbackTags)를 호출해
// 정식 파이프라인과 동일하게 FeedbackResult+FeedbackTag를 채운다. 검증 끝나면 이 파일은 삭제할 것.
@SpringBootTest
class ManualSeedRunner {

    @Autowired private CoachingService coachingService;

    @Test
    void generateCoachingReportForSeededMember() {
        Member member = memberRepository.findById(1L).orElseThrow();
        CoachingReport report = coachingService.generate(member);
        System.out.println("=====COACHING REPORT (id=" + report.getId() + ")=====");
        System.out.println(report.getContent());
    }

    @Autowired private GroqService groqService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FeedbackResultRepository feedbackResultRepository;
    @Autowired private FeedbackTagRepository feedbackTagRepository;
    @Autowired private MemberRepository memberRepository;

    private record Sample(long qId, QuestionType type, String topic, String combo, String category,
                           String question, String answer) {}

    private List<Sample> samples() {
        return List.of(
            new Sample(1, QuestionType.TYPE_1, "공원", "SEED-C1-01", "C1",
                "Describe a park you often visit. What does it look like and why do you enjoy going there?",
                "There is a park near my house. It has trees and a bench. I go sometimes."),
            new Sample(2, QuestionType.TYPE_2, "운동", "SEED-C1-02", "C1",
                "What is your typical exercise routine? Where do you usually work out, and who do you usually go with?",
                "I go to the gym. I go a lot. It is good exercise."),
            new Sample(3, QuestionType.TYPE_3, "영화", "SEED-C1-03", "C1",
                "Tell me about how you became interested in movies. How has that interest changed since then?",
                "I like movies. I watch them a lot. Action movies are good."),
            new Sample(4, QuestionType.TYPE_4, "가족여행", "SEED-C2-04", "C2",
                "Describe the most memorable trip you took with your family. When was it, who was there, and why do you still think about it?",
                "The most memorable trip was definitely last summer, when my whole family — including my grandparents — went to Jeju Island together for the first time in almost ten years. I still think about it because it was the last trip we took before my grandfather passed away, so it means a lot more to me now than it did back then."),
            new Sample(5, QuestionType.TYPE_5, "요리", "SEED-C4-05", "C4",
                "I also enjoy cooking at home. Ask me 3-4 questions about my cooking habits.",
                "Do you cook. What do you cook. Is it good."),
            new Sample(6, QuestionType.TYPE_6, "렌트카", "SEED-C3-06", "C3",
                "Call the rental car company and ask 3-4 questions about reserving a car for your trip.",
                "Hi. I want a car. Do you have car. How much."),
            new Sample(7, QuestionType.TYPE_7, "식당예약", "SEED-C3-07", "C3",
                "You have a reservation problem at a restaurant you booked. Explain the situation to the manager and suggest two or three alternatives.",
                "Hi, I had a reservation for 7pm tonight under my name, but when I arrived just now, the table had already been given to someone else. I understand mistakes happen, so could we either get the next available table, maybe in twenty minutes, or if that's not possible, could you seat us at the bar while we wait? I'd really appreciate it."),
            new Sample(8, QuestionType.TYPE_8, "여행문제", "SEED-C1-08", "C1",
                "Have you ever had a similar problem while traveling? Tell me what happened and how you solved it.",
                "Yes. My flight was late. I waited."),
            new Sample(9, QuestionType.TYPE_9, "카페", "SEED-C5-09", "C5",
                "Compare two cafes you like to visit. How are they similar, and how are they different?",
                "I like two cafes. Cafe A is good. Cafe B is good too."),
            new Sample(10, QuestionType.TYPE_9, "스피커", "SEED-C5-09", "C5",
                "Compare two speakers you own. How are they similar, and how are they different?",
                "I have two speakers. One is loud. One is quiet. I use both."),
            new Sample(11, QuestionType.TYPE_9, "동네공원", "SEED-C5-09", "C5",
                "Compare two parks in your neighborhood. How are they similar, and how are they different?",
                "There are two parks. One is big. One is small. I go to both."),
            new Sample(12, QuestionType.TYPE_10, "환경이슈", "SEED-C5-10", "C5",
                "What are some environmental issues in your country today? What do you think about them?",
                "Air pollution is bad. It is a problem. We should fix it.")
        );
    }

    @Test
    void seedRealFeedbackWithTags() {
        Member member = memberRepository.findById(1L).orElseThrow();

        for (Sample s : samples()) {
            QuestionDto q = new QuestionDto(s.qId(), s.question(), s.topic(), s.type());
            Map<String, Object> fb = groqService.getOpicFeedback(s.answer(), q);

            String mainPointDiag = str(fb.get("mainPoint"));
            String expressionDiag = str(fb.get("expression"));
            String accuracyDiag = str(fb.get("accuracy"));
            String contentDiag = str(fb.get("content"));

            String tagsJson = groqService.extractFeedbackTags(
                    s.type().name(), mainPointDiag, expressionDiag, accuracyDiag, contentDiag);

            int fluencyScore = fluencyScore(s.answer());

            FeedbackResult result = feedbackResultRepository.save(FeedbackResult.builder()
                    .member(member)
                    .questionId(s.qId())
                    .questionType(s.type())
                    .surveyTopicName(s.topic())
                    .comboPatternKey(s.combo())
                    .comboCategory(s.category())
                    .questionContent(s.question())
                    .sttText(s.answer())
                    .mainPoint(mainPointDiag)
                    .mainPointScore(score(fb.get("mainPointScore")))
                    .mainPointQuote(str(fb.get("mainPointQuote")))
                    .mainPointFix(str(fb.get("mainPointFix")))
                    .expression(expressionDiag)
                    .expressionScore(score(fb.get("expressionScore")))
                    .expressionQuote(str(fb.get("expressionQuote")))
                    .expressionFix(str(fb.get("expressionFix")))
                    .accuracy(accuracyDiag)
                    .accuracyScore(score(fb.get("accuracyScore")))
                    .accuracyQuote(str(fb.get("accuracyQuote")))
                    .accuracyFix(str(fb.get("accuracyFix")))
                    .fluency(s.answer().split("\\s+").length + "단어")
                    .fluencyScore(fluencyScore)
                    .content(contentDiag)
                    .contentScore(score(fb.get("contentScore")))
                    .contentQuote(str(fb.get("contentQuote")))
                    .contentFix(str(fb.get("contentFix")))
                    .overall("시드 데이터")
                    .overallGrade("IM2")
                    .improvements(str(fb.get("improvements")))
                    .modelAnswer(str(fb.get("modelAnswer")))
                    .modelAnswerComment(str(fb.get("modelAnswerComment")))
                    .build());

            List<FeedbackTag> tags = parseTags(tagsJson, result);
            feedbackTagRepository.saveAll(tags);

            System.out.println("[" + s.type() + "] saved id=" + result.getId() + ", tags=" + tags.size());
        }
    }

    private List<FeedbackTag> parseTags(String tagsJson, FeedbackResult result) {
        try {
            JsonNode root = objectMapper.readTree(tagsJson);
            List<FeedbackTag> out = new ArrayList<>();
            addTags(out, result, "mainPoint", root.get("mainPoint"));
            JsonNode expr = root.get("expression");
            if (expr != null) {
                addTags(out, result, "vocab", expr.get("vocab"));
                addTags(out, result, "sentence", expr.get("sentence"));
                addTags(out, result, "imagery", expr.get("imagery"));
            }
            addTags(out, result, "accuracy", root.get("accuracy"));
            addTags(out, result, "content", root.get("content"));
            return out;
        } catch (Exception e) {
            System.out.println("태그 파싱 실패: " + e.getMessage());
            return List.of();
        }
    }

    private void addTags(List<FeedbackTag> out, FeedbackResult result, String category, JsonNode arr) {
        if (arr == null || !arr.isArray()) return;
        for (JsonNode n : arr) {
            out.add(FeedbackTag.builder().feedbackResult(result).category(category).tag(n.asText()).build());
        }
    }

    private static int fluencyScore(String text) {
        int words = text.trim().split("\\s+").length;
        if (words >= 130) return 5;
        if (words >= 90) return 4;
        if (words >= 60) return 3;
        if (words >= 30) return 2;
        return 1;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static Integer score(Object o) {
        if (o == null) return null;
        if (o instanceof Integer i) return i;
        try { return Integer.parseInt(o.toString()); } catch (NumberFormatException e) { return null; }
    }
}
