package com.opicnic.opicnic.benchmark;

import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.service.ComboPattern;
import com.opicnic.opicnic.service.QuestionAssemblyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static com.opicnic.opicnic.domain.enums.QuestionType.*;

@SpringBootTest
class QuestionSetCacheBenchmarkTest {

    @Autowired
    QuestionAssemblyService questionAssemblyService;

    private static final ComboPattern C1 = new ComboPattern("C1", List.of(TYPE_1, TYPE_2, TYPE_3));

    @Test
    void measureCacheHitVsMiss() {
        SurveyTopic topic = SurveyTopic.COFFEE_SHOP_GOING;

        // warm-up JVM
        questionAssemblyService.assemble(SurveyTopic.WALKING, C1);

        // 1회: cache miss (DB 조회)
        long t0 = System.nanoTime();
        questionAssemblyService.assemble(topic, C1);
        long missMs = (System.nanoTime() - t0) / 1_000_000;

        // 2~6회: cache hit
        long[] hitTimes = new long[5];
        for (int i = 0; i < 5; i++) {
            long ts = System.nanoTime();
            questionAssemblyService.assemble(topic, C1);
            hitTimes[i] = (System.nanoTime() - ts) / 1_000_000;
        }
        long avgHitMs = (hitTimes[0] + hitTimes[1] + hitTimes[2] + hitTimes[3] + hitTimes[4]) / 5;

        System.out.println("\n========================================");
        System.out.println("  QuestionSet 캐시 벤치마크 결과");
        System.out.println("========================================");
        System.out.printf("  Cache MISS (DB 조회):  %4d ms%n", missMs);
        System.out.printf("  Cache HIT  (평균 5회): %4d ms%n", avgHitMs);
        if (avgHitMs > 0) {
            System.out.printf("  속도 향상:             %4dx%n", missMs / avgHitMs);
        } else {
            System.out.printf("  Cache HIT: < 1ms (측정 불가 수준)%n");
        }
        System.out.println("  HIT 개별: " + hitTimes[0] + "ms, " + hitTimes[1] + "ms, "
                + hitTimes[2] + "ms, " + hitTimes[3] + "ms, " + hitTimes[4] + "ms");
        System.out.println("========================================\n");
    }
}
