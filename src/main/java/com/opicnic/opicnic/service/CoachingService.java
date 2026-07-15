package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opicnic.opicnic.domain.CoachingReport;
import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.FeedbackTag;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.SurveyProfile;
import com.opicnic.opicnic.repository.CoachingReportRepository;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.FeedbackTagRepository;
import com.opicnic.opicnic.repository.SurveyProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.StructuredTaskScope;
import java.util.stream.Collectors;

// 태그 기반 코칭 아키텍처: GroqService.extractFeedbackTags가 답변 단위로 태그를 매기고(LLM 판단),
// 여러 답변에 걸친 집계·문턱값 필터링은 전부 이 클래스가 코드로 한다. LLM은 최종 집계 결과를
// 문장으로 서술하는 역할만 맡는다("판단은 LLM, 집계는 코드"). 자유텍스트를 LLM에게 통째로 주고
// "반복 패턴 요약해줘"라고 시켰던 이전 방식은 카운팅과 의미 클러스터링을 동시에 요구해
// 자기모순 리포트를 냈다.
@Slf4j
@Service
@RequiredArgsConstructor
public class CoachingService {

    private static final int RECENT_RESULTS_LIMIT = 30;
    private static final int MIN_PATTERN_COUNT = 3; // 요소별: 이 미만은 반복 패턴이 아니라 개별 사례로 간주
    private static final double TYPE_PATTERN_RATIO = 0.4; // 유형별: 그 유형 표본 대비 비율 (절대개수 아님 — 표본 크기가 유형마다 다르므로)
    private static final int TYPE_MIN_ATTEMPTS = 2; // 유형별: 최소 표본 수

    private static final Set<String> GROUP_A = Set.of("TYPE_1", "TYPE_2", "TYPE_3", "TYPE_4", "TYPE_8");

    private static final Map<String, String> CATEGORY_TO_ELEMENT = Map.of(
            "mainPoint", "메인포인트",
            "vocab", "표현력", "sentence", "표현력", "imagery", "표현력",
            "accuracy", "정확성",
            "content", "내용 구성"
    );
    private static final List<String> ELEMENT_ORDER = List.of("메인포인트", "표현력", "정확성", "내용 구성");

    private static final Map<String, java.util.function.Function<FeedbackResult, Integer>> ELEMENT_SCORE_GETTER = Map.of(
            "메인포인트", FeedbackResult::getMainPointScore,
            "표현력", FeedbackResult::getExpressionScore,
            "정확성", FeedbackResult::getAccuracyScore,
            "내용 구성", FeedbackResult::getContentScore
    );

    // 태그 -> "왜 약점인지" 짧은 이유. examples[].why를 코드가 채우는 데 씀 (LLM 판단 아님).
    private static final Map<String, String> TAG_REASON = Map.ofEntries(
            Map.entry("WHY_MISSING", "이유가 빠져 있어서"),
            Map.entry("FEELING_MISSING", "감정 표현이 빠져 있어서"),
            Map.entry("MP_LATE", "핵심 포인트가 뒤로 밀려서"),
            Map.entry("FRAME_UNCLEAR", "비교/방향 프레임이 불분명해서"),
            Map.entry("FRAME_LATE", "프레임이 뒤로 밀려서"),
            Map.entry("VOCAB_BASIC", "기본적인 단어 위주라서"),
            Map.entry("SENTENCE_SIMPLE", "단순한 문장 구조라서"),
            Map.entry("IMAGERY_FLAT", "감각적 묘사가 부족해서"),
            Map.entry("TENSE_ERROR", "시제 오류가 있어서"),
            Map.entry("ARTICLE_ERROR", "관사 오류가 있어서"),
            Map.entry("PREPOSITION_ERROR", "전치사 오류가 있어서"),
            Map.entry("SUBJECT_VERB_ERROR", "주어-동사 일치 오류가 있어서"),
            Map.entry("DESCRIPTION_SHALLOW", "묘사가 피상적이라서"),
            Map.entry("CLUE_MISSING", "구체적 단서가 빠져 있어서"),
            Map.entry("STORY_STRUCTURE_WEAK", "이야기 구조가 약해서"),
            Map.entry("TIMELINE_UNCLEAR", "시간 흐름이 불분명해서"),
            Map.entry("REASON_SHALLOW", "이유 설명이 피상적이라서"),
            Map.entry("DIALOGUE_UNNATURAL", "대화가 부자연스러워서"),
            Map.entry("QUESTION_COUNT_SHORT", "질문 개수가 부족해서"),
            Map.entry("ALTERNATIVE_LACKING", "대안 제시가 부족해서"),
            Map.entry("SITUATION_VAGUE", "상황 설명이 모호해서"),
            Map.entry("RESOLUTION_MISSING", "해결 과정이 빠져 있어서"),
            Map.entry("FRAME_MISSING", "비교 프레임이 빠져 있어서"),
            Map.entry("ONE_SIDED", "한쪽 대상만 다뤄서"),
            Map.entry("OPINION_MISSING", "본인 의견이 빠져 있어서"),
            Map.entry("REASON_LACKING", "이유 제시가 부족해서")
    );

    private static final Map<String, String> TYPE_PRACTICE_FOCUS = Map.ofEntries(
            Map.entry("TYPE_1", "감각적 형용사로 묘사를 풍부하게 전개하는 연습"),
            Map.entry("TYPE_2", "when/where/what/frequency/with whom을 구체적으로 서술하는 연습"),
            Map.entry("TYPE_3", "결말/하이라이트를 먼저 말하고 과거 스토리를 전개하는 연습"),
            Map.entry("TYPE_4", "왜 기억에 남는지 먼저 말하는 연습"),
            Map.entry("TYPE_5", "자연스러운 대화체로 질문하는 연습"),
            Map.entry("TYPE_6", "자연스러운 대화체로 응대하는 연습"),
            Map.entry("TYPE_7", "대안을 2~3개 제시하는 연습"),
            Map.entry("TYPE_8", "유사 상황과 해결 과정을 전개하는 연습"),
            Map.entry("TYPE_9", "비교 프레임을 먼저 말하는 연습"),
            Map.entry("TYPE_10", "이슈 제시 후 내 생각을 진술하는 연습")
    );

    private final GroqService groqService;
    private final FeedbackResultRepository feedbackResultRepository;
    private final FeedbackTagRepository feedbackTagRepository;
    private final CoachingReportRepository coachingReportRepository;
    private final ExamPlanService examPlanService;
    private final SurveyProfileRepository surveyProfileRepository;
    private final ObjectMapper objectMapper;

    private record Candidate(Long resultId, String quote, String fix) {}
    private record ExampleItem(String before, String after, String why) {}
    private record ElementSections(String text, Map<String, String> byElement, Map<String, List<ExampleItem>> examplesByElement) {}
    private record TypeSections(String text, Map<String, String> byType) {}

    public CoachingReport generate(Member member) {
        List<FeedbackResult> results = feedbackResultRepository.findByMemberIdOrderByCreatedAtDesc(
                member.getId(), PageRequest.of(0, RECENT_RESULTS_LIMIT));
        String targetGrade = surveyProfileRepository.findByMemberId(member.getId())
                .map(SurveyProfile::getTargetGrade)
                .map(g -> g.label)
                .orElse("IH");

        Map<Long, FeedbackResult> resultById = results.stream()
                .collect(Collectors.toMap(FeedbackResult::getId, r -> r));
        List<FeedbackTag> tags = resultById.isEmpty()
                ? List.of()
                : feedbackTagRepository.findByFeedbackResultIdIn(new ArrayList<>(resultById.keySet()));

        List<ExamPlanService.TypeStat> typeStats = examPlanService.buildWeakTypes(results);
        ElementSections elementSections = buildElementSections(resultById, tags);
        TypeSections typeSections = buildTypeSections(resultById, tags, typeStats);

        Map<String, Double> elementScores = new LinkedHashMap<>();
        for (String element : elementSections.byElement().keySet()) {
            elementScores.put(element, ExamPlanService.weightedAvg(results, ELEMENT_SCORE_GETTER.get(element)));
        }

        String summary = "총 연습 문항 수: " + results.size() + "개\n\n"
                + elementSections.text()
                + typeSections.text();

        String content = groqService.getCoachingReport(summary, targetGrade);
        content = fillGapsAndPostProcess(content, elementSections, elementScores, typeSections, typeStats, targetGrade);

        return coachingReportRepository.save(CoachingReport.builder()
                .member(member)
                .content(content)
                .basedOnCount(results.size())
                .build());
    }

    // 요소별(메인포인트/표현력/정확성/내용구성) 집계 — 태그를 코드가 세고, LLM은 이 결과만 문장으로 씀
    private ElementSections buildElementSections(Map<Long, FeedbackResult> resultById, List<FeedbackTag> tags) {
        Map<String, Integer> counts = new LinkedHashMap<>(); // "category.tag" -> count
        Map<String, List<Candidate>> candidates = new LinkedHashMap<>();

        for (FeedbackTag t : tags) {
            if (t.getTag().endsWith("_GOOD")) continue;
            FeedbackResult r = resultById.get(t.getFeedbackResult().getId());
            if (r == null || r.getQuestionType() == null) continue;
            if (t.getCategory().equals("imagery") && !GROUP_A.contains(r.getQuestionType().name())) continue;

            String key = t.getCategory() + "." + t.getTag();
            counts.merge(key, 1, Integer::sum);

            String quote = quoteFor(t.getCategory(), r);
            String fix = fixFor(t.getCategory(), r);
            if (quote != null && !quote.isBlank() && fix != null && !fix.isBlank()) {
                candidates.computeIfAbsent(key, k -> new ArrayList<>()).add(new Candidate(r.getId(), quote, fix));
            }
        }

        // 요소별로 묶어서 요소 안에서만 분산배정: 요소 간 같은 답변 재사용은 회피, 요소 내 하위태그끼리 겹치는 건 허용(정직한 신호)
        Map<String, List<String>> keysByElement = new LinkedHashMap<>();
        for (String key : counts.keySet()) {
            if (counts.get(key) < MIN_PATTERN_COUNT) continue;
            String element = CATEGORY_TO_ELEMENT.get(key.split("\\.")[0]);
            keysByElement.computeIfAbsent(element, k -> new ArrayList<>()).add(key);
        }

        StringBuilder sb = new StringBuilder();
        Map<String, String> byElement = new LinkedHashMap<>();
        Map<String, List<ExampleItem>> examplesByElement = new LinkedHashMap<>();
        for (String element : ELEMENT_ORDER) {
            List<String> keys = keysByElement.get(element);
            if (keys == null || keys.isEmpty()) continue; // 데이터 없는 요소는 헤더 자체를 안 만듦

            Set<Long> usedInElement = new HashSet<>();
            StringBuilder lines = new StringBuilder();
            List<ExampleItem> examples = new ArrayList<>();
            for (String key : keys) {
                String tag = key.split("\\.")[1];
                int count = counts.get(key);
                List<Candidate> cs = candidates.getOrDefault(key, List.of());
                Candidate chosen = cs.stream().filter(c -> !usedInElement.contains(c.resultId())).findFirst()
                        .orElse(cs.isEmpty() ? null : cs.get(0));
                if (chosen != null) {
                    usedInElement.add(chosen.resultId());
                    examples.add(new ExampleItem(chosen.quote(), chosen.fix(),
                            TAG_REASON.getOrDefault(tag, "반복적으로 나타나는 패턴이라서")));
                }
                String example = chosen != null ? "'" + chosen.quote() + "' -> '" + chosen.fix() + "'" : "null";
                lines.append("- ").append(tag).append(": ").append(count).append("건 example=").append(example).append("\n");
            }
            byElement.put(element, lines.toString());
            examplesByElement.put(element, examples);
            sb.append("【").append(element).append("】\n").append(lines).append("\n");
        }
        return new ElementSections(sb.toString(), byElement, examplesByElement);
    }

    // 유형별(TYPE_1~10) 집계 — 같은 태그 데이터를 questionType 축으로 한 번 더 그룹핑. 새 LLM 호출 아님.
    // 반환하는 byType.keySet()이 곧 "비율 판정을 실제로 통과한 유형" — fillGapsAndPostProcess에서 LLM 응답 대조 필터링에 씀.
    private TypeSections buildTypeSections(Map<Long, FeedbackResult> resultById, List<FeedbackTag> tags,
                                            List<ExamPlanService.TypeStat> typeStats) {
        Map<String, Integer> typeAttempts = typeStats.stream()
                .collect(Collectors.toMap(ExamPlanService.TypeStat::typeKey, ExamPlanService.TypeStat::count));

        Map<String, Integer> counts = new LinkedHashMap<>(); // "TYPE_9|category.tag" -> count
        for (FeedbackTag t : tags) {
            if (t.getTag().endsWith("_GOOD")) continue;
            FeedbackResult r = resultById.get(t.getFeedbackResult().getId());
            if (r == null || r.getQuestionType() == null) continue;
            if (t.getCategory().equals("imagery") && !GROUP_A.contains(r.getQuestionType().name())) continue;

            String key = r.getQuestionType().name() + "|" + t.getCategory() + "." + t.getTag();
            counts.merge(key, 1, Integer::sum);
        }

        Map<String, List<String>> linesByType = new LinkedHashMap<>();
        for (var e : counts.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            String typeKey = parts[0];
            int attempts = typeAttempts.getOrDefault(typeKey, 0);
            // 절대개수가 아니라 비율로 판정: 표본 크기가 유형마다 달라서("5회 중 2회"와 "20회 중 2회"는 다른 신호) 절대개수 비교는 부적절
            if (attempts < TYPE_MIN_ATTEMPTS) continue;
            if ((double) e.getValue() / attempts < TYPE_PATTERN_RATIO) continue;

            String tag = parts[1].split("\\.")[1];
            linesByType.computeIfAbsent(typeKey, k -> new ArrayList<>())
                    .add("- " + tag + ": " + e.getValue() + "/" + attempts + "건");
        }

        StringBuilder sb = new StringBuilder();
        Map<String, String> byType = new LinkedHashMap<>();
        for (var e : linesByType.entrySet()) {
            String lines = String.join("\n", e.getValue()) + "\n";
            byType.put(e.getKey(), lines);
            sb.append("【유형: ").append(e.getKey()).append("】\n").append(lines).append("\n");
        }
        return new TypeSections(sb.toString(), byType);
    }

    private String quoteFor(String category, FeedbackResult r) {
        return switch (category) {
            case "mainPoint" -> r.getMainPointQuote();
            case "vocab", "sentence", "imagery" -> r.getExpressionQuote();
            case "accuracy" -> r.getAccuracyQuote();
            case "content" -> r.getContentQuote();
            default -> null;
        };
    }

    private String fixFor(String category, FeedbackResult r) {
        return switch (category) {
            case "mainPoint" -> r.getMainPointFix();
            case "vocab", "sentence", "imagery" -> r.getExpressionFix();
            case "accuracy" -> r.getAccuracyFix();
            case "content" -> r.getContentFix();
            default -> null;
        };
    }

    // LLM 응답 후처리: (1) 빠진 criteria/types를 갭필 콜로 채우고 (2) this_week는 LLM 호출 없이 코드로 확정,
    // (3) criteria는 weakCriteria 모양(score+examples[])으로 코드가 재조립하고, types는 label/strategy를 코드가 붙이고
    // 자격 없는(비율 판정 미통과) 항목은 걸러낸다. score/examples 전부 코드 — LLM은 analysis/advice 텍스트만 씀.
    private String fillGapsAndPostProcess(String reportJson, ElementSections elementSections, Map<String, Double> elementScores,
                                           TypeSections typeSections, List<ExamPlanService.TypeStat> typeStats, String targetGrade) {
        try {
            Map<String, String> typeLabels = typeStats.stream()
                    .collect(Collectors.toMap(ExamPlanService.TypeStat::typeKey, ExamPlanService.TypeStat::typeLabel));

            ObjectNode root = (ObjectNode) objectMapper.readTree(reportJson);

            ArrayNode criteria = root.has("criteria") && root.get("criteria").isArray()
                    ? (ArrayNode) root.get("criteria") : objectMapper.createArrayNode();
            criteria = fillMissingCriteria(criteria, elementSections.byElement(), targetGrade);

            ArrayNode weakCriteria = objectMapper.createArrayNode();
            for (JsonNode item : criteria) {
                String name = item.path("name").asText(null);
                if (name == null || !elementSections.byElement().containsKey(name)) continue; // 지시 위반 방어
                ObjectNode entry = objectMapper.createObjectNode();
                entry.put("name", name);
                entry.put("score", elementScores.getOrDefault(name, 0.0));
                entry.put("analysis", item.path("analysis").asText(""));
                entry.put("advice", item.path("advice").asText(""));
                ArrayNode examples = objectMapper.createArrayNode();
                for (ExampleItem ex : elementSections.examplesByElement().getOrDefault(name, List.of())) {
                    ObjectNode exNode = objectMapper.createObjectNode();
                    exNode.put("before", ex.before());
                    exNode.put("after", ex.after());
                    exNode.put("why", ex.why());
                    examples.add(exNode);
                }
                entry.set("examples", examples);
                weakCriteria.add(entry);
            }
            root.remove("criteria");
            root.set("weakCriteria", weakCriteria);

            ArrayNode rawTypes = root.has("types") && root.get("types").isArray()
                    ? (ArrayNode) root.get("types") : objectMapper.createArrayNode();
            rawTypes = fillMissingTypes(rawTypes, typeSections.byType(), typeLabels);

            ArrayNode rebuiltTypes = objectMapper.createArrayNode();
            for (JsonNode item : rawTypes) {
                String typeKey = item.path("typeKey").asText(null);
                if (typeKey == null || !typeSections.byType().containsKey(typeKey)) continue; // 지시 위반 방어
                ObjectNode entry = objectMapper.createObjectNode();
                entry.put("label", typeLabels.getOrDefault(typeKey, typeKey));
                entry.put("pattern", item.path("pattern").asText(""));
                entry.put("strategy", TYPE_PRACTICE_FOCUS.getOrDefault(typeKey, "약한 유형 집중 연습"));
                rebuiltTypes.add(entry);
            }
            root.set("types", rebuiltTypes);

            Optional<ExamPlanService.TypeStat> weakest = typeStats.stream().filter(t -> t.count() > 0).findFirst();
            if (weakest.isPresent()) {
                ExamPlanService.TypeStat t = weakest.get();
                String focus = TYPE_PRACTICE_FOCUS.getOrDefault(t.typeKey(), "약한 유형 집중 연습");
                root.put("this_week", t.typeLabel() + " 유형 " + t.count() + "회 연습 — " + focus);
            }

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("리포트 후처리 실패, 원본 반환: {}", e.getMessage());
            return reportJson;
        }
    }

    // criteria에서 빠진 요소(코드가 집계했는데 LLM이 안 쓴 것)를 찾아, 각각 독립적인 "이거 하나만 써" 콜로 병렬 보충.
    // 셀 게 없는 요청이라 여기서 또 빠뜨릴 수 없음 — 재시도가 아니라 애초에 배치 자체를 안 만드는 설계.
    private ArrayNode fillMissingCriteria(ArrayNode criteria, Map<String, String> byElement, String targetGrade) throws Exception {
        Set<String> present = new HashSet<>();
        criteria.forEach(item -> present.add(item.path("name").asText("")));
        List<String> missing = byElement.keySet().stream().filter(e -> !present.contains(e)).toList();
        if (missing.isEmpty()) return criteria;

        log.warn("criteria 누락 감지, 갭필 진행: {}", missing);
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            Map<String, StructuredTaskScope.Subtask<Map<String, Object>>> subtasks = new LinkedHashMap<>();
            for (String element : missing) {
                subtasks.put(element, scope.fork(() -> groqService.writeCriterion(element, byElement.get(element), targetGrade)));
            }
            scope.joinUntil(Instant.now().plus(Duration.ofSeconds(30)));
            scope.throwIfFailed();

            for (var e : subtasks.entrySet()) {
                Map<String, Object> result = e.getValue().get();
                ObjectNode entry = objectMapper.createObjectNode();
                entry.put("name", e.getKey());
                entry.put("analysis", String.valueOf(result.getOrDefault("analysis", "")));
                entry.put("advice", String.valueOf(result.getOrDefault("advice", "")));
                criteria.add(entry);
            }
        }
        return criteria;
    }

    // types에서 빠진 유형을 찾아 같은 방식으로 병렬 보충 (typeKey+pattern만, label/strategy는 이후 단계에서 코드가 붙임).
    private ArrayNode fillMissingTypes(ArrayNode types, Map<String, String> byType, Map<String, String> typeLabels) throws Exception {
        Set<String> present = new HashSet<>();
        types.forEach(item -> present.add(item.path("typeKey").asText("")));
        List<String> missing = byType.keySet().stream().filter(k -> !present.contains(k)).toList();
        if (missing.isEmpty()) return types;

        log.warn("types 누락 감지, 갭필 진행: {}", missing);
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            Map<String, StructuredTaskScope.Subtask<String>> subtasks = new LinkedHashMap<>();
            for (String typeKey : missing) {
                String label = typeLabels.getOrDefault(typeKey, typeKey);
                subtasks.put(typeKey, scope.fork(() -> groqService.writeTypePattern(typeKey, label, byType.get(typeKey))));
            }
            scope.joinUntil(Instant.now().plus(Duration.ofSeconds(30)));
            scope.throwIfFailed();

            for (var e : subtasks.entrySet()) {
                JsonNode parsed = objectMapper.readTree(e.getValue().get());
                ObjectNode entry = objectMapper.createObjectNode();
                entry.put("typeKey", e.getKey());
                entry.put("pattern", parsed.path("pattern").asText(""));
                types.add(entry);
            }
        }
        return types;
    }
}
