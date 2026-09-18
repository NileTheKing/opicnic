package com.opicnic.opicnic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// S2 측정용 실패 주입 스위치(STT_MOCK_429_RATE/STT_MOCK_5XX_RATE) 회귀 테스트.
// enabled=false(mock) 경로에만 있는 로직이라 실제 Groq 호출 없이 STTService를 직접 생성해서 검증한다.
class STTServiceMockFailureInjectionTest {

    private STTService newMockSttService(double rate429, double rate5xx) {
        return new STTService(RestClient.builder(), "dummy-key", false, 0L, rate429, rate5xx, new ObjectMapper(), new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("STT_MOCK_429_RATE=1.0이면 항상 429(HttpClientErrorException)를 던진다")
    void rate429One_alwaysThrows429() {
        STTService sttService = newMockSttService(1.0, 0.0);

        assertThatThrownBy(() -> sttService.sendStreamToStt(new byte[]{1}, "a.webm"))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    @DisplayName("STT_MOCK_5XX_RATE=1.0이면 항상 503(HttpServerErrorException)을 던진다")
    void rate5xxOne_alwaysThrows5xx() {
        STTService sttService = newMockSttService(0.0, 1.0);

        assertThatThrownBy(() -> sttService.sendStreamToStt(new byte[]{1}, "a.webm"))
                .isInstanceOf(HttpServerErrorException.class)
                .satisfies(e -> assertThat(((HttpServerErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    @DisplayName("두 rate가 0(기본값)이면 기존처럼 항상 성공한다")
    void ratesZero_alwaysSucceeds() {
        STTService sttService = newMockSttService(0.0, 0.0);

        String result = sttService.sendStreamToStt(new byte[]{1}, "a.webm");

        assertThat(result).isNotBlank();
    }
}
