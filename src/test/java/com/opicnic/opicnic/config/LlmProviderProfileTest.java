package com.opicnic.opicnic.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// LLM_PROVIDER 한 단어로 application-<공급자>.yml 묶음이 통째로 들어오는지 (application.yml의 spring.profiles.include)
class LlmProviderProfileTest {

    @Configuration
    static class Empty {}

    // 운영에선 LLM_PROVIDER가 환경변수로 들어온다 — 기본 속성(.properties)은 프로필 결정에 안 쓰여서 시스템 속성으로 흉내낸다
    // 키도 시스템 속성으로 고정한다 — 기본 속성은 셸의 진짜 환경변수보다 우선순위가 낮아서, 키가 있으면 실제 키가 읽혀 실패 메시지에 찍혔다
    private Map<String, String> boot(String... given) {
        String[] props = new String[given.length + 2];
        System.arraycopy(given, 0, props, 0, given.length);
        props[given.length] = "GROQ_API_KEY=g";
        props[given.length + 1] = "GEMINI_API_KEY=m";
        for (String p : props) System.setProperty(p.substring(0, p.indexOf('=')), p.substring(p.indexOf('=') + 1));
        try (var ctx = new SpringApplicationBuilder(Empty.class).web(WebApplicationType.NONE).run()) {
            var env = ctx.getEnvironment();
            Map<String, String> out = new HashMap<>();
            for (String k : new String[]{"spring.ai.openai.base-url", "spring.ai.openai.api-key", "spring.ai.openai.chat.completions-path",
                    "spring.ai.openai.chat.options.model", "spring.ai.tagging.model", "opicnic.llm.reasoning-effort"}) {
                out.put(k, env.getProperty(k));
            }
            return out;
        } finally {
            for (String p : props) System.clearProperty(p.substring(0, p.indexOf('=')));
        }
    }

    @Test
    void 기본은_groq() {
        var env = boot();
        assertThat(env.get("spring.ai.openai.base-url")).isEqualTo("https://api.groq.com/openai");
        assertThat(env.get("spring.ai.openai.api-key")).isEqualTo("g");
        assertThat(env.get("spring.ai.openai.chat.options.model")).isEqualTo("openai/gpt-oss-120b");
        assertThat(env.get("opicnic.llm.reasoning-effort")).isEqualTo("low");
    }

    @Test
    void LLM_PROVIDER_gemini면_묶음이_통째로_바뀐다() {
        var env = boot("LLM_PROVIDER=gemini");
        assertThat(env.get("spring.ai.openai.base-url")).isEqualTo("https://generativelanguage.googleapis.com/v1beta/openai");
        assertThat(env.get("spring.ai.openai.api-key")).isEqualTo("m");
        assertThat(env.get("spring.ai.openai.chat.completions-path")).isEqualTo("/chat/completions");
        assertThat(env.get("spring.ai.openai.chat.options.model")).isEqualTo("gemini-3.5-flash-lite");
        assertThat(env.get("spring.ai.tagging.model")).isEqualTo("gemini-3.5-flash-lite");
        assertThat(env.get("opicnic.llm.reasoning-effort")).isEmpty();
    }

    @Test
    void prod_프로필과_같이_켜져도_공급자_묶음이_들어온다() {
        var env = boot("spring.profiles.active=prod", "LLM_PROVIDER=gemini", "DOMAIN=x", "DB_USERNAME=u", "DB_PASSWORD=p");
        assertThat(env.get("spring.ai.openai.chat.options.model")).isEqualTo("gemini-3.5-flash-lite");
    }
}
