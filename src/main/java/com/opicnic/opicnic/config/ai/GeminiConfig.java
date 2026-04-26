package com.opicnic.opicnic.config.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class GeminiConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "ai.gemini.enabled", havingValue = "false")
    public ChatModel mockChatModel() {
        return new MockChatModel();
    }

    // ai.gemini.enabled가 true이거나 설정이 없으면 
    // 기본적으로 생성되는 GoogleGenAiChatModel 빈이 사용됩니다.
}
