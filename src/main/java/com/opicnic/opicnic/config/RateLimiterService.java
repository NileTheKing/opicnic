package com.opicnic.opicnic.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 사용자별 시간당 문항 한도 버킷. RateLimitInterceptor(단순 1건당 1소비 경로)와
// PracticeAttemptApiController(검증 통과 후 실제 문항 수만큼 소비)가 공유해서 쓴다.
// 검증 실패로 끝날 요청까지 미리 소비하지 않도록, "먼저 검증 다 통과한 뒤 여기서 소비"하는
// 순서를 호출하는 쪽이 지켜야 한다 — 이 서비스 자체는 순서를 강제하지 않는다.
@Component
public class RateLimiterService {

    public static final int CAPACITY_PER_HOUR = 15;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private Bucket getBucket(String key) {
        return buckets.computeIfAbsent(key, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(CAPACITY_PER_HOUR)
                                .refillIntervally(CAPACITY_PER_HOUR, Duration.ofHours(1))
                                .build())
                        .build()
        );
    }

    public boolean tryConsume(int cost) {
        return getBucket(getUserKey()).tryConsume(cost);
    }

    private String getUserKey() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OAuth2User oAuth2User) {
            String providerId = oAuth2User.getAttribute("providerId");
            if (providerId != null) return "user:" + providerId;
        }
        return "anonymous";
    }
}
