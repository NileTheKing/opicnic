package com.opicnic.opicnic.storage;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

// 테스트·R2 미설정 로컬용. presignPut은 실제로 올릴 수 없는 URL을 돌려주므로 put()으로 직접 넣는다.
public class InMemoryAudioStorage implements AudioStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    public void put(String key, byte[] bytes) {
        objects.put(key, bytes);
    }

    @Override
    public URL presignPut(String key, long contentLength, String contentType, Duration ttl) {
        try {
            return new URL("http://in-memory-storage/" + key);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public byte[] read(String key) {
        byte[] bytes = objects.get(key);
        if (bytes == null) throw new NoSuchElementException("객체 없음: " + key);
        return bytes;
    }

    @Override
    public boolean exists(String key) {
        return objects.containsKey(key);
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }
}
