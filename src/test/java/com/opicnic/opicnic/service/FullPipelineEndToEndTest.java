package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.dto.QuestionDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 Groq API를 호출하는 통합 테스트 (GROQ_API_KEY, LLM_ENABLED=true 필요).
// CoachingService.generate()가 실제로 구현하는 요소별+유형별 태그 집계 알고리즘을 여기서 먼저 검증한다.
@SpringBootTest
class FullPipelineEndToEndTest {

    @Autowired
    private GroqService groqService;
    @Autowired
    private ObjectMapper objectMapper;

    private static final Set<String> GROUP_A = Set.of("TYPE_1", "TYPE_2", "TYPE_3", "TYPE_4", "TYPE_8");
    private static final int MIN_PATTERN_COUNT = 3;
    private static final double TYPE_PATTERN_RATIO = 0.4;
    private static final int TYPE_MIN_ATTEMPTS = 2;

    // 10개 TYPE, 다양한 품질(약함/보통/좋음)이 섞이도록 구성 — 시드 스크립트 때 쓴 실제 답변 텍스트 재사용.
    // TYPE_9는 유형별 집계 검증을 위해 3건(원본 1건 + 프레임 없는 약한 답변 2건)으로 구성.
    private record Sample(long qId, QuestionType type, String topic, String question, String answer) {}

    private List<Sample> buildSamples() {
        return List.of(
            new Sample(1, QuestionType.TYPE_1, "공원", "Describe a park you often visit. What does it look like and why do you enjoy going there?",
                "There is a park near my house. It has trees and a bench. I go sometimes."),
            new Sample(2, QuestionType.TYPE_2, "운동", "What is your typical exercise routine? Where do you usually work out, and who do you usually go with?",
                "I go to the gym. I go a lot. It is good exercise."),
            new Sample(3, QuestionType.TYPE_3, "영화", "Tell me about how you became interested in movies. How has that interest changed since then?",
                "I like movies. I watch them a lot. Action movies are good."),
            new Sample(4, QuestionType.TYPE_4, "가족여행", "Describe the most memorable trip you took with your family. When was it, who was there, and why do you still think about it?",
                "The most memorable trip was definitely last summer, when my whole family — including my grandparents — went to Jeju Island together for the first time in almost ten years. I still think about it because it was the last trip we took before my grandfather passed away, so it means a lot more to me now than it did back then."),
            new Sample(5, QuestionType.TYPE_5, "요리", "I also enjoy cooking at home. Ask me 3-4 questions about my cooking habits.",
                "Do you cook. What do you cook. Is it good."),
            new Sample(6, QuestionType.TYPE_6, "렌트카", "Call the rental car company and ask 3-4 questions about reserving a car for your trip.",
                "Hi. I want a car. Do you have car. How much."),
            new Sample(7, QuestionType.TYPE_7, "식당예약", "You have a reservation problem at a restaurant you booked. Explain the situation to the manager and suggest two or three alternatives.",
                "Hi, I had a reservation for 7pm tonight under my name, but when I arrived just now, the table had already been given to someone else. I understand mistakes happen, so could we either get the next available table, maybe in twenty minutes, or if that's not possible, could you seat us at the bar while we wait? I'd really appreciate it."),
            new Sample(8, QuestionType.TYPE_8, "여행문제", "Have you ever had a similar problem while traveling? Tell me what happened and how you solved it.",
                "Yes. My flight was late. I waited."),
            new Sample(9, QuestionType.TYPE_9, "카페", "Compare two cafes you like to visit. How are they similar, and how are they different?",
                "I usually split my time between two cafes near my place. One is really quiet with big tables, so I go there whenever I need to study or focus. The other has much better coffee and a livelier atmosphere, so I go there instead when I'm meeting friends. They're similar in that both are within walking distance, but the vibe is completely different."),
            new Sample(10, QuestionType.TYPE_10, "환경이슈", "What are some environmental issues in your country today? What do you think about them?",
                "Air pollution is bad. It is a problem. We should fix it."),
            new Sample(11, QuestionType.TYPE_9, "스피커", "Compare two speakers you own. How are they similar, and how are they different?",
                "I have two speakers. One is loud. One is quiet. I use both."),
            new Sample(12, QuestionType.TYPE_9, "동네공원", "Compare two parks in your neighborhood. How are they similar, and how are they different?",
                "There are two parks. One is big. One is small. I go to both.")
        );
    }

    private record Candidate(int sampleIndex, String quote, String fix) {}

    @Test
    void runFullNewPipelineOnRealisticAnswers() throws Exception {
        List<Sample> samples = buildSamples();

        Map<String, Integer> elementCounts = new LinkedHashMap<>(); // "cat.tag" -> count
        Map<String, List<Candidate>> elementCandidates = new LinkedHashMap<>();

        Map<QuestionType, Integer> typeAttempts = new LinkedHashMap<>();
        Map<String, Integer> typeTagCounts = new LinkedHashMap<>(); // "TYPE_9|cat.tag" -> count

        for (int i = 0; i < samples.size(); i++) {
            Sample s = samples.get(i);
            QuestionDto q = new QuestionDto(s.qId(), s.question(), s.topic(), s.type());
            Map<String, Object> callA = groqService.getOpicFeedback(s.answer(), q);
            typeAttempts.merge(s.type(), 1, Integer::sum);

            System.out.println("--- [" + i + "] " + s.type() + " (" + s.topic() + ") ---");
            System.out.println("scores: MP=" + callA.get("mainPointScore") + " EX=" + callA.get("expressionScore")
                    + " AC=" + callA.get("accuracyScore") + " CT=" + callA.get("contentScore"));

            String tagsJson = groqService.extractFeedbackTags(
                    s.type().name(), str(callA.get("mainPoint")), str(callA.get("expression")),
                    str(callA.get("accuracy")), str(callA.get("content")));
            JsonNode node = objectMapper.readTree(tagsJson);
            System.out.println("tags: " + tagsJson);

            List<String> cats = new ArrayList<>(List.of("mainPoint", "vocab", "sentence", "accuracy", "content"));
            if (GROUP_A.contains(s.type().name())) cats.add("imagery");

            JsonNode expressionNode = node.get("expression");

            for (String cat : cats) {
                boolean isExpressionSubAxis = cat.equals("vocab") || cat.equals("sentence") || cat.equals("imagery");
                JsonNode arr = isExpressionSubAxis
                        ? (expressionNode != null ? expressionNode.get(cat) : null)
                        : node.get(cat);
                if (arr == null || !arr.isArray()) continue;
                for (JsonNode tagNode : arr) {
                    String tagName = tagNode.asText();
                    if (tagName.endsWith("_GOOD")) continue;
                    String key = cat + "." + tagName;
                    elementCounts.merge(key, 1, Integer::sum);
                    typeTagCounts.merge(s.type().name() + "|" + key, 1, Integer::sum);

                    String quote = switch (cat) {
                        case "mainPoint" -> str(callA.get("mainPointQuote"));
                        case "vocab", "sentence", "imagery" -> str(callA.get("expressionQuote"));
                        case "accuracy" -> str(callA.get("accuracyQuote"));
                        case "content" -> str(callA.get("contentQuote"));
                        default -> "";
                    };
                    String fix = switch (cat) {
                        case "mainPoint" -> str(callA.get("mainPointFix"));
                        case "vocab", "sentence", "imagery" -> str(callA.get("expressionFix"));
                        case "accuracy" -> str(callA.get("accuracyFix"));
                        case "content" -> str(callA.get("contentFix"));
                        default -> "";
                    };
                    if (!quote.isBlank() && !fix.isBlank()) {
                        elementCandidates.computeIfAbsent(key, k -> new ArrayList<>()).add(new Candidate(i, quote, fix));
                    }
                }
            }
        }

        // 원본 태깅 카테고리(6개, flat) -> 실제 리포트 상위 요소(4개)로 코드가 직접 그룹핑.
        Map<String, String> categoryToElement = Map.of(
                "mainPoint", "메인포인트",
                "vocab", "표현력", "sentence", "표현력", "imagery", "표현력",
                "accuracy", "정확성",
                "content", "내용 구성"
        );
        List<String> elementOrder = List.of("메인포인트", "표현력", "정확성", "내용 구성");

        // 요소별 섹션: 요소 안에서만 예시 분산배정 (요소 간 재사용 회피, 요소 내 하위태그 겹침은 허용)
        Map<String, List<String>> keysByElement = new LinkedHashMap<>();
        for (String key : elementCounts.keySet()) {
            if (elementCounts.get(key) < MIN_PATTERN_COUNT) continue;
            keysByElement.computeIfAbsent(categoryToElement.get(key.split("\\.")[0]), k -> new ArrayList<>()).add(key);
        }

        StringBuilder summary = new StringBuilder();
        summary.append("총 연습 문항 수: ").append(samples.size()).append("개\n\n");
        for (String element : elementOrder) {
            List<String> keys = keysByElement.get(element);
            if (keys == null || keys.isEmpty()) continue;
            Set<Integer> usedInElement = new HashSet<>();
            summary.append("【").append(element).append("】\n");
            for (String key : keys) {
                String tag = key.split("\\.")[1];
                int count = elementCounts.get(key);
                List<Candidate> cs = elementCandidates.getOrDefault(key, List.of());
                Candidate chosen = cs.stream().filter(c -> !usedInElement.contains(c.sampleIndex())).findFirst()
                        .orElse(cs.isEmpty() ? null : cs.get(0));
                if (chosen != null) usedInElement.add(chosen.sampleIndex());
                String ex = chosen != null ? "'" + chosen.quote() + "' -> '" + chosen.fix() + "'" : "null";
                summary.append("- ").append(tag).append(": ").append(count).append("/").append(samples.size())
                        .append("건 example=").append(ex).append("\n");
            }
            summary.append("\n");
        }

        // 유형별 섹션: 같은 태그 데이터를 questionType 축으로 재집계. 절대개수가 아니라 그 유형 표본 대비 비율로 판정.
        Map<String, List<String>> linesByType = new LinkedHashMap<>();
        for (var e : typeTagCounts.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            QuestionType type = QuestionType.valueOf(parts[0]);
            int attempts = typeAttempts.getOrDefault(type, 0);
            if (attempts < TYPE_MIN_ATTEMPTS) continue;
            if ((double) e.getValue() / attempts < TYPE_PATTERN_RATIO) continue;
            String tag = parts[1].split("\\.")[1];
            linesByType.computeIfAbsent(parts[0], k -> new ArrayList<>())
                    .add("- " + tag + ": " + e.getValue() + "/" + attempts + "건");
        }
        for (var e : linesByType.entrySet()) {
            summary.append("【유형: ").append(e.getKey()).append("】\n");
            e.getValue().forEach(l -> summary.append(l).append("\n"));
            summary.append("\n");
        }

        System.out.println("=====FINAL STRUCTURED SUMMARY (요소별+유형별, Call2 입력)=====");
        System.out.println(summary);

        // TYPE_9는 3건 중 2건이 프레임 없는 약한 답변이라 비율(0.4) 조건을 넘겨 유형 섹션이 생겨야 함
        assertThat(summary.toString()).contains("【유형: TYPE_9】");

        String finalReport = groqService.getCoachingReport(summary.toString(), "IH");
        System.out.println("=====FINAL COACHING REPORT=====");
        System.out.println(finalReport);

        JsonNode report = objectMapper.readTree(finalReport);
        assertThat(report.has("criteria")).isTrue();
        assertThat(report.get("criteria").isArray()).isTrue();
        assertThat(report.has("types")).isTrue();
        assertThat(report.get("types").isArray()).isTrue();
        // 회귀 체크: 요소별 섹션이 있었다면 criteria가 비어있으면 안 됨 (예전 instruction-overload 회귀 재발 여부 확인용)
        if (!keysByElement.isEmpty()) {
            assertThat(report.get("criteria")).isNotEmpty();
        }
    }

    private String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
