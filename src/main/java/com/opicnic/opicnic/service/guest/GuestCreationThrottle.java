package com.opicnic.opicnic.service.guest;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

// 게스트는 만드는 게 공짜라서, 새로 만들 때마다 개인 한도가 새로 생긴다. IP당 시간당 생성 수를 막아 우회를 줄인다.
// 버킷은 한 시간 안 쓰이면 버려진다(메모리 상한). 앱 인스턴스가 하나라 인메모리로 충분하다(RateLimiterService와 같은 전제).
@Component
@RequiredArgsConstructor
public class GuestCreationThrottle {

    private final GuestProperties properties;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(2))
            .maximumSize(100_000)
            .build();

    public boolean tryAcquire(String ip) {
        int limit = Math.max(1, properties.getIpHourlyCreations());
        Bucket bucket = buckets.get(ip, k -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(limit).refillIntervally(limit, Duration.ofHours(1)).build())
                .build());
        return bucket.tryConsume(1);
    }

    // 운영은 Cloudflare -> 호스트 nginx -> 앱이다. forward-headers-strategy=framework가 X-Forwarded-For의 마지막 홉(= Cloudflare 엣지일 수 있음)을
    // 원격 주소로 주므로, Cloudflare가 붙이는 원 방문자 IP 헤더를 먼저 본다. 원본 서버로 직접 들어오는 요청은 이 헤더를 위조할 수 있다 — 한도 보호 수준의 신뢰만 둔다.
    public static String clientIp(HttpServletRequest request) {
        String cf = request.getHeader("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) return cf.trim();
        return request.getRemoteAddr();
    }
}
