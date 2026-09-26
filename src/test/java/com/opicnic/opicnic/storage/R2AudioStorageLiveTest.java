package com.opicnic.opicnic.storage;

import com.opicnic.opicnic.config.StorageConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 R2에 붙는 연결 확인. R2_ACCOUNT_ID가 셸에 있을 때만 돈다 — `set -a && source .env && set +a` 후
// `./gradlew test --tests R2AudioStorageLiveTest`. 평소 `./gradlew test`에는 딸려 돌지 않는다.
// 검증: presigned PUT으로 올린 바이트를 서버가 읽을 수 있고, 서명한 크기와 다른 PUT은 거절된다.
@EnabledIfEnvironmentVariable(named = "R2_ACCOUNT_ID", matches = ".+")
class R2AudioStorageLiveTest {

    private final AudioStorage storage = new StorageConfig().audioStorage(
            System.getenv("R2_ACCOUNT_ID"), System.getenv("R2_ACCESS_KEY_ID"),
            System.getenv("R2_SECRET_ACCESS_KEY"), System.getenv().getOrDefault("R2_BUCKET", "opicnic-audio"), false);

    @Test
    void presignedPutThenReadThenDelete() throws Exception {
        String key = "pending/live-test-" + UUID.randomUUID() + "/q0.webm";
        byte[] payload = "not-really-webm".getBytes();
        HttpClient http = HttpClient.newHttpClient();
        try {
            URL url = storage.presignPut(key, payload.length, "audio/webm", Duration.ofMinutes(2));

            HttpResponse<String> ok = http.send(HttpRequest.newBuilder(url.toURI())
                    .header("Content-Type", "audio/webm")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(payload)).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(ok.statusCode()).as(ok.body()).isEqualTo(200);

            assertThat(storage.exists(key)).isTrue();
            assertThat(storage.read(key)).isEqualTo(payload);

            // 서명한 크기(15바이트)와 다른 본문은 거절돼야 한다 — 직접 업로드의 크기 상한이 이 서명에 걸려 있다
            HttpResponse<String> wrongSize = http.send(HttpRequest.newBuilder(url.toURI())
                    .header("Content-Type", "audio/webm")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[payload.length + 1])).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(wrongSize.statusCode()).isEqualTo(403);
        } finally {
            storage.delete(key);
            assertThat(storage.exists(key)).isFalse();
        }
    }
}
