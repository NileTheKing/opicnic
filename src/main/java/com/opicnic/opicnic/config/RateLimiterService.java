package com.opicnic.opicnic.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
    private final Environment environment;

    public RateLimiterService(Environment environment) {
        this.environment = environment;
    }

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

    // FU-03: PracticeAttemptApiController가 답변 제출 경로에서 호출한다. attemptMemberId==null이고
    // dev 프로파일이면(=DevPracticeController가 로그인 세션 없이 만든 k6 부하테스트 attempt) 버킷을
    // 아예 소비하지 않는다 — 유한 용량의 또 다른 공유 버킷을 만드는 대신 이 조합만 완전히 예외 처리한다.
    // dev의 로그인 회원 attempt(memberId != null)와 production의 익명 요청은 계속 기존 한도(시간당
    // CAPACITY_PER_HOUR)를 그대로 적용받는다.
    public boolean tryConsume(int cost, Long attemptMemberId) {
        if (attemptMemberId == null && isDevProfileActive()) {
            return true;
        }
        return tryConsume(cost);
    }

    private String getUserKey() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OAuth2User oAuth2User) {
            String providerId = oAuth2User.getAttribute("providerId");
            if (providerId != null) return "user:" + providerId;
        }
        return "anonymous";
    }

    // FU-03: 활성 프로파일 배열만 직접 보면 "활성 프로파일 없음 + spring.profiles.default=dev"인
    // 기본 실행 상태(운영 배포에서도 흔히 쓰는 형태)를 못 잡는다. acceptsProfiles()는 활성 프로파일이
    // 없을 때 default 프로파일로 판정을 대신하므로 이 경우도 dev로 인식한다.
    private boolean isDevProfileActive() {
        return environment.acceptsProfiles(Profiles.of("dev"));
    }
}
