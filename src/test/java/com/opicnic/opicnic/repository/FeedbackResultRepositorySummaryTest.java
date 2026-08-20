package com.opicnic.opicnic.repository;

import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.domain.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// PERF-01 회귀 테스트: findSummaryByMemberId의 JPQL constructor expression이 실제 DB에 대해
// 컴파일뿐 아니라 런타임으로도 올바르게 매핑되는지(필드 순서/타입 불일치는 컴파일로 못 잡는다),
// 그리고 TEXT 컬럼(sttText 등)은 실제로 안 실려오는지(null)를 검증한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class FeedbackResultRepositorySummaryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private FeedbackResultRepository feedbackResultRepository;
    @Autowired
    private MemberRepository memberRepository;

    @Test
    void summaryProjectionMapsFieldsCorrectlyAndOmitsTextColumns() {
        Member member = memberRepository.save(Member.builder()
                .provider("kakao").providerId("p1").nickname("닉네임").role(Role.USER).build());

        FeedbackResult full = FeedbackResult.builder()
                .member(member)
                .questionType(QuestionType.TYPE_1)
                .surveyTopicName("MOVIE_WATCHING")
                .comboCategory("C1")
                .overallGrade("IM2")
                .mainPointScore(3).expressionScore(4).accuracyScore(3).contentScore(4).fluencyScore(3)
                .sttText("이건 TEXT 컬럼 — 요약 화면에서 안 실려와야 한다")
                .build();
        feedbackResultRepository.save(full);

        List<FeedbackResult> summaries = feedbackResultRepository.findSummaryByMemberId(member.getId());

        assertThat(summaries).hasSize(1);
        FeedbackResult summary = summaries.get(0);
        assertThat(summary.getQuestionType()).isEqualTo(QuestionType.TYPE_1);
        assertThat(summary.getSurveyTopicName()).isEqualTo("MOVIE_WATCHING");
        assertThat(summary.getComboCategory()).isEqualTo("C1");
        assertThat(summary.getOverallGrade()).isEqualTo("IM2");
        assertThat(summary.getMainPointScore()).isEqualTo(3);
        assertThat(summary.getExpressionScore()).isEqualTo(4);
        assertThat(summary.getAccuracyScore()).isEqualTo(3);
        assertThat(summary.getContentScore()).isEqualTo(4);
        assertThat(summary.getFluencyScore()).isEqualTo(3);
        // JPQL constructor expression이 select 목록에 넣지 않은 필드 — DB에서 아예 안 읽어온다.
        assertThat(summary.getSttText()).isNull();
    }
}
