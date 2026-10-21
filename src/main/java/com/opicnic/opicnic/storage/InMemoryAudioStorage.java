package com.opicnic.opicnic.storage;

import com.opicnic.opicnic.exception.AudioNotFoundException;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// 테스트·R2 미설정 로컬용. 기본 presignPut은 실제로 올릴 수 없는 URL을 돌려주므로 put()으로 직접 넣는다.
// uploadBase를 주면(용량·장애 실험의 메모리 저장소) presign URL이 그 주소의 dev 업로드 엔드포인트(DevAudioUploadController)를 가리킨다.
// 같은 내용의 오디오는 한 번만 보관한다(내용 해시로 공유) — 부하 실험에서 같은 녹음이 초당 수십 번 올라와도 메모리가 쌓이지 않게.
// 서로 다른 내용의 총량은 상한(MAX_DISTINCT_BYTES)을 넘으면 거절한다.
public class InMemoryAudioStorage implements AudioStorage {

    static final long MAX_DISTINCT_BYTES = 256L * 1024 * 1024;

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final Map<String, byte[]> distinct = new ConcurrentHashMap<>();
    private final AtomicLong distinctBytes = new AtomicLong();
    private final String uploadBase;

    public InMemoryAudioStorage() {
        this(null);
    }

    public InMemoryAudioStorage(String uploadBase) {
        this.uploadBase = uploadBase == null || uploadBase.isBlank() ? null : uploadBase.replaceAll("/+$", "");
    }

    public void put(String key, byte[] bytes) {
        String hash = sha256(bytes);
        byte[] shared = distinct.get(hash);
        if (shared == null) {
            if (distinctBytes.addAndGet(bytes.length) > MAX_DISTINCT_BYTES) {
                distinctBytes.addAndGet(-bytes.length);
                throw new IllegalStateException("인메모리 오디오 저장소 상한 초과 (서로 다른 내용 " + MAX_DISTINCT_BYTES / (1024 * 1024) + "MB)");
            }
            shared = distinct.putIfAbsent(hash, bytes);
            if (shared == null) shared = bytes; else distinctBytes.addAndGet(-bytes.length);
        }
        objects.put(key, shared);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public URL presignPut(String key, long contentLength, String contentType, Duration ttl) {
        try {
            return new URL(uploadBase != null ? uploadBase + "/api/dev/audio/" + key : "http://in-memory-storage/" + key);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public byte[] read(String key) {
        byte[] bytes = objects.get(key);
        if (bytes == null) throw new AudioNotFoundException(key, null);
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
