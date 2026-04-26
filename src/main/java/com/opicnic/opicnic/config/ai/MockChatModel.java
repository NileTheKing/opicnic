package com.opicnic.opicnic.config.ai;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 부하 테스트를 위한 가짜 ChatModel.
 * 실제 Gemini API를 호출하지 않고 지연 시간만 시뮬레이션합니다.
 */
public class MockChatModel implements ChatModel {

    @Override
    public ChatResponse call(Prompt prompt) {
        simulateDelay();

        // 가짜 JSON 응답 생성
        String mockJsonResponse = """
                {
                  "vocabulary": "Excellent choice of words.",
                  "grammar": "Perfect grammar usage.",
                  "mainPoint": "Very clear and concise.",
                  "fluency": "Fluent like a native speaker.",
                  "content": "Relevant to the topic.",
                  "overall": "AL (Advanced Low)",
                  "improvements": "Keep practicing with various topics."
                }
                """;

        AssistantMessage assistantMessage = new AssistantMessage(mockJsonResponse);
        Generation generation = new Generation(assistantMessage);
        
        return new ChatResponse(List.of(generation));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // 스트리밍 호출 시에도 대응
        return Flux.just(call(prompt));
    }

    private void simulateDelay() {
        try {
            // 실제 API 지연 시간 시뮬레이션 (500ms)
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
