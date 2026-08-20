package com.opicnic.opicnic.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opicnic.opicnic.domain.QuestionSet;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// DOC-01: 이 테스트는 원래 /admin/question-sets(form POST) 관리자 CRUD를 검증했지만, 그 CRUD가
// REST API(AdminQuestionSetApiController, /api/admin/question-sets)로 이전되면서 옛 경로 자체가
// 사라졌고, SEC-01(ADMIN 권한 강제)/SEC-06(CSRF 재활성화) 이후로는 인증·CSRF 없이 호출하면
// 403으로 막혀 "Docker 없어서 실패"가 아니라 테스트 계약 자체가 구식이 되어 있었다(PROJECT.md의
// 예전 설명과 실제 실패 원인이 달랐다). 현재 라우트 + ADMIN 인증 + CSRF 토큰으로 다시 작성한다.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
public class QuestionSetAdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuestionSetRepository questionSetRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Container
    public static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @BeforeEach
    void setUp() {
        questionSetRepository.deleteAll();
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("새로운 질문 세트를 성공적으로 생성하고 저장해야 한다")
    void testCreateQuestionSet() throws Exception {
        String body = objectMapper.writeValueAsString(new QuestionSetPayload(null, "새로운 테스트 세트", SurveyTopic.TECHNOLOGY));

        mockMvc.perform(post("/api/admin/question-sets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        List<QuestionSet> foundSets = questionSetRepository.findAll();
        assertThat(foundSets).hasSize(1);
        assertThat(foundSets.get(0).getName()).isEqualTo("새로운 테스트 세트");
        assertThat(foundSets.get(0).getTopic()).isEqualTo(SurveyTopic.TECHNOLOGY);
        assertThat(foundSets.get(0).isDeleted()).isFalse();
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("기존 질문 세트를 성공적으로 수정해야 한다")
    void testUpdateQuestionSet() throws Exception {
        QuestionSet existingSet = new QuestionSet("기존 세트", SurveyTopic.MOVIE_WATCHING);
        questionSetRepository.save(existingSet);

        String body = objectMapper.writeValueAsString(
                new QuestionSetPayload(existingSet.getId(), "수정된 테스트 세트", SurveyTopic.HEALTH_WELLNESS));

        mockMvc.perform(put("/api/admin/question-sets/{id}", existingSet.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        QuestionSet foundSet = questionSetRepository.findById(existingSet.getId()).orElse(null);
        assertThat(foundSet).isNotNull();
        assertThat(foundSet.getName()).isEqualTo("수정된 테스트 세트");
        assertThat(foundSet.getTopic()).isEqualTo(SurveyTopic.HEALTH_WELLNESS);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("질문 세트를 성공적으로 논리적 삭제해야 한다")
    void testSoftDeleteQuestionSet() throws Exception {
        QuestionSet existingSet = new QuestionSet("삭제될 세트", SurveyTopic.MUSIC_LISTENING);
        questionSetRepository.save(existingSet);

        mockMvc.perform(delete("/api/admin/question-sets/{id}", existingSet.getId())
                        .with(csrf()))
                .andExpect(status().isNoContent());

        List<QuestionSet> activeSets = questionSetRepository.findAll();
        assertThat(activeSets).isEmpty();
    }

    @Test
    @WithMockUser(authorities = "USER")
    @DisplayName("ADMIN 권한이 없으면 생성 요청이 거부되어야 한다")
    void nonAdminCannotCreateQuestionSet() throws Exception {
        String body = objectMapper.writeValueAsString(new QuestionSetPayload(null, "세트", SurveyTopic.TECHNOLOGY));

        mockMvc.perform(post("/api/admin/question-sets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        assertThat(questionSetRepository.findAll()).isEmpty();
    }

    private record QuestionSetPayload(Long id, String name, SurveyTopic topic) {
    }
}
