package com.opicnic.opicnic.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryAudioStorageTest {

    @Test
    @DisplayName("기본 presign URL은 지금 그대로, uploadBase를 주면 dev 업로드 엔드포인트를 가리킨다")
    void presignUrl() throws Exception {
        assertThat(new InMemoryAudioStorage().presignPut("k/1", 1, "audio/webm", Duration.ofMinutes(1)).toString())
                .isEqualTo("http://in-memory-storage/k/1");
        assertThat(new InMemoryAudioStorage("http://app:8080/").presignPut("pending/a/q0.webm", 1, "audio/webm", Duration.ofMinutes(1)).toString())
                .isEqualTo("http://app:8080/api/dev/audio/pending/a/q0.webm");
    }

    @Test
    @DisplayName("같은 내용은 한 번만 보관한다(서로 다른 키가 같은 배열을 공유)")
    void identicalContentIsShared() {
        var storage = new InMemoryAudioStorage();
        storage.put("a", new byte[]{1, 2, 3});
        storage.put("b", new byte[]{1, 2, 3});
        assertThat(storage.read("a")).isSameAs(storage.read("b"));
        storage.put("c", new byte[]{9});
        assertThat(storage.read("c")).containsExactly(9);
        storage.delete("a");
        assertThat(storage.exists("a")).isFalse();
        assertThat(storage.read("b")).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("서로 다른 내용의 총량이 상한을 넘으면 거절한다")
    void distinctBytesAreCapped() {
        var storage = new InMemoryAudioStorage();
        byte[] big = new byte[64 * 1024 * 1024];
        for (int i = 0; i < 4; i++) {
            big[0] = (byte) i;
            storage.put("k" + i, big.clone());
        }
        big[0] = 99;
        assertThatThrownBy(() -> storage.put("over", big.clone())).isInstanceOf(IllegalStateException.class);
    }
}
