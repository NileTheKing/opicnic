package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.dto.ComboQuestionsResult;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.dto.FeedbackTagDto;
import com.opicnic.opicnic.dto.QuestionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;



import java.util.ArrayList;
import java.util.List;
import java.util.Map;





@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {

    private final ComboPracticeService comboPracticeService;
    private final STTService sttService;
    private final GroqService groqService;
    private final ObjectMapper objectMapper;

    public ComboQuestionsResult getComboQuestions(String topic, String difficulty) {
        return comboPracticeService.getComboQuestions(topic, difficulty);
    }

    // 단일 문항 채점 (ADR-0001). 재시도 루프가 없다 — 재시도는 워커가 문항을 다시 집는 것(ScoringJobItem.attempts)으로
    // 소유한다. 여기서 내부 재시도까지 하면 3×3=9회가 된다. 워커는 STT 성공분을 DB(ScoringJobItem.sttText)에 남겨
    // 재시도·재시작 후에도 STT를 다시 부르지 않는다 — 채점 LLM만 실패해도 STT부터 다시 부르던 옛 동기 루프가
    // 429를 자가 유발했다(2026-08-31 실측: 15문항 재시도로 STT 43건 중 22건만 성공, Whisper RPM 20 자가 초과).
    // 자기소개(questionType null)는 채점 문항이 아니므로 LLM 호출 없이 완료 처리하고, 저장은
    // FeedbackPersistenceService.saveOne이 questionType==null을 걸러 문항 개수 통계에 섞이지 않게 한다.
    public String transcribe(byte[] audio, String filename) {
        return sttService.sendStreamToStt(audio, filename);
    }

    public FeedbackDTO gradeWithSpeech(String speechText, QuestionDto question) {
        if (speechText == null || speechText.trim().split("\\s+").length < 5) {
            return noResponseDto(question, speechText);
        }
        if (question.getQuestionType() == null) {
            return selfIntroductionDto(question, speechText);
        }
        var feedbackMap = groqService.getOpicFeedback(speechText, question);

        String mainPointDiag = str(feedbackMap, "mainPoint");
        String expressionDiag = str(feedbackMap, "expression");
        String accuracyDiag = str(feedbackMap, "accuracy");
        String contentDiag = str(feedbackMap, "content");

        String tagsJson = groqService.extractFeedbackTags(
                question.getQuestionType().name(),
                mainPointDiag, expressionDiag, accuracyDiag, contentDiag);
        List<FeedbackTagDto> tags = parseTags(tagsJson, question.getQuestionType().name());

        int fluencyScore = computeFluencyScore(speechText);
        // TYPE_5~7(롤플레이)은 MP를 "평가 제외"로 0 고정 반환하도록 프롬프트에 지시했다.
        // 이 0을 다른 4개 점수와 그대로 평균 내면 롤플레이를 연습할수록 등급이 구조적으로
        // 낮아진다 (SCORE-02). null로 바꿔두면 computeGrade/computeOverallText/
        // ExamPlanService.weightedAvg가 이미 null을 평균 분모에서 제외하므로 자동으로 해결된다.
        boolean mpExcluded = isRoleplayType(question.getQuestionType());
        Integer mpScore = mpExcluded ? null : score(feedbackMap, "mainPointScore");
        int exScore    = score(feedbackMap, "expressionScore");
        int acScore    = score(feedbackMap, "accuracyScore");
        int ctScore    = score(feedbackMap, "contentScore");
        String grade   = computeGrade(mpScore, exScore, acScore, fluencyScore, ctScore);

        String mainPointQuote = str(feedbackMap, "mainPointQuote");
        String mainPointFix = str(feedbackMap, "mainPointFix");
        String expressionQuote = str(feedbackMap, "expressionQuote");
        String expressionFix = str(feedbackMap, "expressionFix");
        String accuracyQuote = str(feedbackMap, "accuracyQuote");
        String accuracyFix = str(feedbackMap, "accuracyFix");
        String contentQuote = str(feedbackMap, "contentQuote");
        String contentFix = str(feedbackMap, "contentFix");

        return FeedbackDTO.builder()
                .question(question)
                .sttText(speechText)
                .mainPoint(reassemble(mainPointDiag, mainPointQuote, mainPointFix))
                .mainPointScore(mpScore)
                .mainPointQuote(mainPointQuote)
                .mainPointFix(mainPointFix)
                .expression(reassemble(expressionDiag, expressionQuote, expressionFix))
                .expressionScore(exScore)
                .expressionQuote(expressionQuote)
                .expressionFix(expressionFix)
                .accuracy(reassemble(accuracyDiag, accuracyQuote, accuracyFix))
                .accuracyScore(acScore)
                .accuracyQuote(accuracyQuote)
                .accuracyFix(accuracyFix)
                .fluency(computeFluencyText(speechText, fluencyScore))
                .fluencyScore(fluencyScore)
                .content(reassemble(contentDiag, contentQuote, contentFix))
                .contentScore(ctScore)
                .contentQuote(contentQuote)
                .contentFix(contentFix)
                .overall(computeOverallText(grade, mpScore, exScore, acScore, fluencyScore, ctScore))
                .overallGrade(grade)
                .improvements(reassemble(str(feedbackMap, "improvements"),
                        str(feedbackMap, "improvementsQuote"), str(feedbackMap, "improvementsFix")))
                .modelAnswer(str(feedbackMap, "modelAnswer"))
                .modelAnswerComment(str(feedbackMap, "modelAnswerComment"))
                .tags(tags)
                .build();

    }

    // STT는 RestClient가 HttpClientErrorException(429)을 던지지만, 채점/태깅은 Spring AI ChatModel을
    // 거치면서 NonTransientAiException("429 - …")으로 바뀌고 원인 체인이 없다. 예외 타입만 보면
    // 실제 LLM 429가 rate-limit 분기(긴 백오프)를 못 타고 일반 백오프로 떨어진다 — 2026-09-18 계측
    // 작업 중 발견. ExternalCallMetrics.outcomeOf()가 두 경로를 다 판정하므로 그걸 재사용한다.
    public static boolean isRateLimited(Throwable e) {
        return "429".equals(ExternalCallMetrics.outcomeOf(e));
    }

    private static FeedbackDTO selfIntroductionDto(QuestionDto question, String speechText) {
        return FeedbackDTO.builder()
                .question(question)
                .sttText(speechText)
                .overall("자기소개는 채점 대상이 아닙니다. 수고하셨어요!")
                .build();
    }

    // FU-02: 5단어 미만 "무응답" 조기 반환은 questionType을 보지 않고 mainPointScore=1을 항상 넣었다.
    // 정상 길이 응답은 이미 isRoleplayType()으로 TYPE_5~7의 MP를 null(평가 제외)로 처리하는데(SCORE-02),
    // 짧은 응답만 이 규칙을 우회해 롤플레이 무응답도 "핵심전달 1점" 표본으로 잘못 쌓였다.
    private static FeedbackDTO noResponseDto(QuestionDto question, String speechText) {
        Integer mainPointScore = isRoleplayType(question.getQuestionType()) ? null : 1;
        return FeedbackDTO.builder()
                .question(question)
                .sttText(speechText)
                .overall("응답이 감지되지 않았습니다.")
                .overallGrade("IL")
                .mainPointScore(mainPointScore).expressionScore(1).accuracyScore(1).fluencyScore(1).contentScore(1)
                .improvements("답변을 녹음해주세요.")
                .build();
    }

    private static String computeFluencyText(String text, int score) {
        int words = (text == null || text.isBlank()) ? 0 : text.trim().split("\\s+").length;
        return switch (score) {
            case 5 -> words + "단어. 발화량이 충분합니다.";
            case 4 -> words + "단어. 조금 더 말하면 만점이에요. (목표: 130단어+)";
            case 3 -> words + "단어. 발화량을 더 늘려보세요. (목표: 90단어+)";
            case 2 -> words + "단어. 더 길게 말하는 연습이 필요해요. (목표: 60단어+)";
            default -> words + "단어. 발화량이 많이 부족해요.";
        };
    }

    private static String computeOverallText(String grade, Integer... scores) {
        String[] labels = {"핵심전달", "표현력", "정확성", "발화량", "내용전개"};
        int minScore = 5;
        String weakest = null;
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] != null && scores[i] < minScore) {
                minScore = scores[i];
                weakest = labels[i];
            }
        }
        String base = grade + " 수준입니다.";
        return weakest != null ? base + " " + weakest + " 개선이 다음 목표예요." : base;
    }

    private static int computeFluencyScore(String text) {
        if (text == null || text.isBlank()) return 1;
        int words = text.trim().split("\\s+").length;
        if (words >= 130) return 5;
        if (words >= 90)  return 4;
        if (words >= 60)  return 3;
        if (words >= 30)  return 2;
        return 1;
    }

    private static boolean isRoleplayType(QuestionType type) {
        return type == QuestionType.TYPE_5 || type == QuestionType.TYPE_6 || type == QuestionType.TYPE_7;
    }

    private static String computeGrade(Integer... scores) {
        double avg = 0;
        int count = 0;
        for (Integer s : scores) {
            if (s != null) { avg += s; count++; }
        }
        if (count == 0) return "IM1";
        avg /= count;
        if (avg >= 4.5) return "AL";
        if (avg >= 3.8) return "IH";
        if (avg >= 3.2) return "IM3";
        if (avg >= 2.6) return "IM2";
        if (avg >= 2.0) return "IM1";
        return "IL";
    }

    // LLM이 진단/quote/fix를 분리해 반환해도, 화면(feedback.html)은 기존 "진단 + 예) ..." 한 덩어리 텍스트를 그대로 기대하므로 저장 직전에 재조합
    private static String reassemble(String diagnosis, String quote, String fix) {
        String base = diagnosis == null ? "" : diagnosis;
        if (quote != null && !quote.isBlank() && fix != null && !fix.isBlank()) {
            return base + "\n예) '" + quote + "' -> '" + fix + "'";
        }
        return base;
    }

    // 태깅 콜의 중첩 스키마({"mainPoint":[],"expression":{"vocab":[],"sentence":[],"imagery":[]},"accuracy":[],"content":[]})를
    // FeedbackTag row로 저장하기 쉽게 (category, tag) 평탄화. questionType은 mainPoint/content의
    // allowlist가 유형별로 달라 필요하다(FeedbackTagVocabulary 참고).
    private List<FeedbackTagDto> parseTags(String tagsJson, String questionType) {
        try {
            JsonNode root = objectMapper.readTree(tagsJson);
            List<FeedbackTagDto> result = new ArrayList<>();
            addTags(result, "mainPoint", root.get("mainPoint"), FeedbackTagVocabulary.mainPointOptions(questionType));
            JsonNode expression = root.get("expression");
            if (expression != null) {
                addTags(result, "vocab", expression.get("vocab"), FeedbackTagVocabulary.EXPRESSION_VOCAB);
                addTags(result, "sentence", expression.get("sentence"), FeedbackTagVocabulary.EXPRESSION_SENTENCE);
                addTags(result, "imagery", expression.get("imagery"), FeedbackTagVocabulary.EXPRESSION_IMAGERY);
            }
            addTags(result, "accuracy", root.get("accuracy"), FeedbackTagVocabulary.ACCURACY);
            addTags(result, "content", root.get("content"), FeedbackTagVocabulary.contentOptions(questionType));
            return result;
        } catch (Exception e) {
            log.warn("태그 파싱 실패, 빈 목록 반환: {}", e.getMessage());
            return List.of();
        }
    }

    // AI-01: LLM이 과도하게 긴 문자열을 태그로 반환해도 그대로 DB에 안 들어가게 길이를 제한하고,
    // 빈 문자열은 애초에 태그로서 의미가 없으므로 버린다.
    private static final int MAX_TAG_LENGTH = 60;
    // REVIEW-09: allowlist 검증만으로는 "유효한 태그를 비정상적으로 많이" 반환하는 경우(반복/환각)를
    // 못 막는다. 카테고리당 상한을 둬 한 카테고리가 통계를 도배하지 않도록 한다.
    private static final int MAX_TAGS_PER_CATEGORY = 5;

    // FU-06: 이전엔 allowlist를 통과한 동일 태그를 상한(5개)까지 그대로 중복 추가했다.
    // CoachingService가 태그 row 개수를 "발생 횟수"로 세므로, 답변 하나가 같은 태그를 5번
    // 반환하면 그것만으로 MIN_PATTERN_COUNT(3)를 채워 패턴처럼 보고되는 문제가 있었다.
    // LinkedHashSet으로 답변(카테고리) 하나당 같은 태그는 최초 1회만 남기고, 중복/blank/unknown은
    // 상한(distinct 개수 기준)을 소비하지 않는다.
    private static void addTags(List<FeedbackTagDto> result, String category, JsonNode arr,
                                 java.util.Set<String> allowlist) {
        if (arr == null || !arr.isArray()) return;
        java.util.Set<String> distinctTags = new java.util.LinkedHashSet<>();
        for (JsonNode n : arr) {
            if (distinctTags.size() >= MAX_TAGS_PER_CATEGORY) break;
            String tag = n.asText();
            if (tag == null || tag.isBlank()) continue;
            if (tag.length() > MAX_TAG_LENGTH) tag = tag.substring(0, MAX_TAG_LENGTH);
            // REVIEW-09: 프롬프트가 지정한 고정 어휘 밖의 태그(환각/오타/스키마 이탈)는 저장하지 않는다.
            if (!allowlist.contains(tag)) continue;
            distinctTags.add(tag); // 이미 있던 태그면 size가 늘지 않아 상한을 소비하지 않는다.
        }
        for (String tag : distinctTags) {
            result.add(new FeedbackTagDto(category, tag));
        }
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    // AI-01: LLM 응답도 신뢰 경계 밖 입력이다. 점수 필드가 없거나 파싱이 안 되거나 1~5 범위를
    // 벗어나면(예: score=99) 조용히 null/기본값으로 넘기지 않고 예외를 던져, 이미 이 서브태스크를
    // 감싸고 있는 재시도 루프가 "LLM 응답 품질 실패"로 취급해 재시도하도록 한다. 예전엔 score()가
    // null을 반환하면 이 값을 그대로 int로 언박싱하는 호출부에서 NPE가 났고, 또는 mainPointScore의
    // 경우 "롤플레이라 평가 제외"와 "파싱 실패"가 똑같이 null로 뭉쳐져 통계에서 구분이 안 됐다.
    private static int score(Map<String, Object> map, String key) {
        Object v = map.get(key);
        Integer parsed = null;
        if (v instanceof Integer i) {
            parsed = i;
        } else if (v != null) {
            try { parsed = Integer.parseInt(v.toString()); } catch (NumberFormatException ignored) { }
        }
        if (parsed == null || parsed < 1 || parsed > 5) {
            throw new IllegalStateException("AI 응답의 점수 필드가 유효하지 않습니다: " + key + "=" + v);
        }
        return parsed;
    }

}
