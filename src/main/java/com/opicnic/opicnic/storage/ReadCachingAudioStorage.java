package com.opicnic.opicnic.storage;

import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 실험 전용(opicnic.storage.read-cache=true, scripts/retry-storm.sh). 워커는 시도마다 R2에서 음성을 다시 읽는데,
// 랩탑 wifi에선 동시 읽기가 수십 초씩 걸려 그 대기가 재시도 속도를 대신 늦춘다 — 재시도 정책의 효과가 가려진다.
// 한 번 읽은 음성을 메모리에 두어 R2 왕복을 실험 변수에서 뺀다. 운영에선 쓰지 않는다(힙에 음성을 쌓지 않는 게 전환 이유).
public class ReadCachingAudioStorage implements AudioStorage {

    private final AudioStorage delegate;
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

    public ReadCachingAudioStorage(AudioStorage delegate) {
        this.delegate = delegate;
    }

    @Override
    public URL presignPut(String key, long contentLength, String contentType, Duration ttl) {
        return delegate.presignPut(key, contentLength, contentType, ttl);
    }

    @Override
    public byte[] read(String key) {
        return cache.computeIfAbsent(key, delegate::read);
    }

    @Override
    public boolean exists(String key) {
        return delegate.exists(key);
    }

    @Override
    public void delete(String key) {
        cache.remove(key);
        delegate.delete(key);
    }
}
