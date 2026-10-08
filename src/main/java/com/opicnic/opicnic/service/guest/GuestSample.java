package com.opicnic.opicnic.service.guest;

import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.enums.QuestionType;

import java.time.LocalDateTime;
import java.util.List;

// 게스트에게 복사해 줄 예시 기록 (classpath:guest/sample.json). 운영 DB에 원본 회원을 따로 두지 않고 코드와 같이 배포한다 —
// 운영 DB에 손으로 넣을 일이 없고, 로컬·운영이 같은 예시를 본다.
// 실제 채점 파이프라인으로 만든다: GuestSampleGenerator(테스트 소스)가 대본 답변을 채점해 이 파일을 쓴다.
public record GuestSample(List<Result> results, Report coaching) {

    public record Tag(String category, String tag) {}

    public record Report(String content, int basedOnCount) {}

    public record Result(
            Long questionId, String attemptId, QuestionType questionType, String surveyTopicName,
            String comboPatternKey, String comboCategory, String questionContent, String sttText,
            String mainPoint, Integer mainPointScore, String mainPointQuote, String mainPointFix,
            String expression, Integer expressionScore, String expressionQuote, String expressionFix,
            String accuracy, Integer accuracyScore, String accuracyQuote, String accuracyFix,
            String content, Integer contentScore, String contentQuote, String contentFix,
            String fluency, Integer fluencyScore,
            String overall, String overallGrade, String improvements,
            String modelAnswer, String modelAnswerComment,
            LocalDateTime createdAt, List<Tag> tags) {

        public static Result of(FeedbackResult r, List<Tag> tags) {
            return new Result(r.getQuestionId(), r.getAttemptId(), r.getQuestionType(), r.getSurveyTopicName(),
                    r.getComboPatternKey(), r.getComboCategory(), r.getQuestionContent(), r.getSttText(),
                    r.getMainPoint(), r.getMainPointScore(), r.getMainPointQuote(), r.getMainPointFix(),
                    r.getExpression(), r.getExpressionScore(), r.getExpressionQuote(), r.getExpressionFix(),
                    r.getAccuracy(), r.getAccuracyScore(), r.getAccuracyQuote(), r.getAccuracyFix(),
                    r.getContent(), r.getContentScore(), r.getContentQuote(), r.getContentFix(),
                    r.getFluency(), r.getFluencyScore(),
                    r.getOverall(), r.getOverallGrade(), r.getImprovements(),
                    r.getModelAnswer(), r.getModelAnswerComment(),
                    r.getCreatedAt(), tags);
        }

        FeedbackResult toEntity(Member guest, String newAttemptId) {
            return FeedbackResult.builder()
                    .member(guest)
                    .questionId(questionId).attemptId(newAttemptId)
                    .questionType(questionType).surveyTopicName(surveyTopicName)
                    .comboPatternKey(comboPatternKey).comboCategory(comboCategory)
                    .questionContent(questionContent).sttText(sttText)
                    .mainPoint(mainPoint).mainPointScore(mainPointScore).mainPointQuote(mainPointQuote).mainPointFix(mainPointFix)
                    .expression(expression).expressionScore(expressionScore).expressionQuote(expressionQuote).expressionFix(expressionFix)
                    .accuracy(accuracy).accuracyScore(accuracyScore).accuracyQuote(accuracyQuote).accuracyFix(accuracyFix)
                    .content(content).contentScore(contentScore).contentQuote(contentQuote).contentFix(contentFix)
                    .fluency(fluency).fluencyScore(fluencyScore)
                    .overall(overall).overallGrade(overallGrade).improvements(improvements)
                    .modelAnswer(modelAnswer).modelAnswerComment(modelAnswerComment)
                    .sample(true)
                    .build();
        }
    }
}
