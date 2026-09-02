package com.opicnic.opicnic.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.dto.QuestionDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 수동 측정 전용. 채점 호출(GroqService.getOpicFeedback) 1건의 completion 토큰이 maxTokens 상한에
// 얼마나 붙는지를 "최악 케이스(2분 발화 분량 ~280단어)"로 재는 것이 목적이다.
// STT는 태우지 않고 하드코딩 transcript를 쓴다. -Dmanual.scoringtokens=true 로만 켜진다.
// 실제 Groq API를 호출하므로 임의 실행 금지.
@EnabledIfSystemProperty(named = "manual.scoringtokens", matches = "true")
public class ManualScoringTokenMeasurementTest {

    // 2분 발화 분량(약 280단어)의 긴 영어 답변. STT 결과처럼 filler/더듬음/문법 오류를 섞었다.
    private static final String LONG_TRANSCRIPT = """
            Okay so, um, let me tell you about the park that I usually go to near my house. It's, uh,
            it's called Hangang Park and I think I go there like three or four times every week, mostly
            in the early morning before I go to work. The park is really big, I mean it's huge, and there
            is a long walking path along the river and a lot of tall trees on both side of the path.
            When I first moved to this neighborhood about two years ago I didn't know the park was there,
            but my neighbor told me about it and after that I started going almost every day. Um, what I
            like the most is the air in the morning, it's very fresh and cool, and there is almost nobody
            there except some old people doing exercise and a few, uh, a few people running with their dogs.
            I usually bring my earphones and listen to some podcast while I am walking, and sometimes I just
            sit on the bench near the water and watch the river for like twenty minutes. Last summer I
            actually go there almost every single day because the weather was so hot in my apartment and
            the park was much more cooler. There was one day, I remember, when I saw a really beautiful
            sunrise, the sky was all orange and pink, and I take a lot of pictures with my phone and I send
            them to my mother. She said it was very pretty. So yeah, I think the park is very important for
            me because it help me to relax and to clear my head before the busy day start, and honestly
            without it I think my life would be much more stressful than now. That's basically all about it.
            """;

    // Spring 컨텍스트/DB 없이 GroqService만 직접 조립한다 — 재려는 건 채점 호출 1건의 토큰뿐이라
    // 애플리케이션 전체를 띄울 이유가 없다. 모델/base-url은 application.yml과 동일하게 맞춘다.
    private GroqService newGroqService() {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl("https://api.groq.com/openai")
                .apiKey(System.getProperty("GROQ_API_KEY"))
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model("openai/gpt-oss-120b").build())
                .build();
        GroqService service = new GroqService(chatModel, new ObjectMapper());
        ReflectionTestUtils.setField(service, "aiEnabled", true);
        ReflectionTestUtils.setField(service, "mockDelayMs", 0L);
        ReflectionTestUtils.setField(service, "taggingModel", "openai/gpt-oss-20b");
        return service;
    }

    @Test
    @DisplayName("긴 답변 채점 1건의 토큰 사용량 실측")
    void measure() {
        QuestionDto question = new QuestionDto(1L,
                "I would like to know about a park in your neighborhood. What does it look like? "
                        + "Please describe the park you often go to in as much detail as possible.",
                "공원 가기", QuestionType.TYPE_1);

        Logger groqLogger = (Logger) LoggerFactory.getLogger(GroqService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        groqLogger.setLevel(Level.INFO);
        groqLogger.addAppender(appender);

        Map<String, Object> result = null;
        Exception thrown = null;
        long start = System.currentTimeMillis();
        try {
            result = newGroqService().getOpicFeedback(LONG_TRANSCRIPT, question);
        } catch (Exception e) {
            thrown = e;
        }
        long ms = System.currentTimeMillis() - start;

        groqLogger.detachAppender(appender);
        List<String> usageLines = new ArrayList<>();
        for (ILoggingEvent e : appender.list) {
            if (e.getFormattedMessage().contains("[TOKEN-DEBUG]")) usageLines.add(e.getFormattedMessage());
        }

        String line = "=".repeat(78);
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(line).append("\n### SCORING TOKEN MEASUREMENT ###\n").append(line).append("\n");
        sb.append("transcript words = ").append(LONG_TRANSCRIPT.trim().split("\\s+").length).append("\n");
        sb.append("elapsed = ").append(ms).append(" ms\n");
        sb.append("usage logs:\n");
        for (String u : usageLines) sb.append("  ").append(u).append("\n");
        sb.append("parse = ").append(thrown == null ? "OK" : "FAILED: " + thrown).append("\n");
        if (result != null) {
            sb.append("keys = ").append(result.keySet()).append("\n");
            for (String k : List.of("mainPoint", "improvements", "modelAnswer")) {
                Object v = result.get(k);
                String s = String.valueOf(v);
                sb.append("  ").append(k).append(" = ").append(s.substring(0, Math.min(300, s.length()))).append("\n");
            }
        }
        sb.append(line).append("\n");
        System.out.println(sb);

        if (thrown != null) throw new RuntimeException(thrown);
    }
}
