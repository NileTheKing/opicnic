package com.opicnic.opicnic.admin;

import com.opicnic.opicnic.domain.QuestionSet;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
public class QuestionSetAdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuestionSetRepository questionSetRepository;

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

    @AfterEach
    void tearDown() {
    }

    @Test
    @DisplayName("새로운 질문 세트를 성공적으로 생성하고 저장해야 한다")
    void testCreateQuestionSet() throws Exception {
        String name = "새로운 테스트 세트";
        SurveyTopic topic = SurveyTopic.TECHNOLOGY;

        mockMvc.perform(post("/admin/question-sets")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", name)
                        .param("topic", topic.name()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/question-sets"));

        List<QuestionSet> foundSets = questionSetRepository.findAll();
        assertThat(foundSets).hasSize(1);
        assertThat(foundSets.get(0).getName()).isEqualTo(name);
        assertThat(foundSets.get(0).getTopic()).isEqualTo(topic);
        assertThat(foundSets.get(0).isDeleted()).isFalse();
    }

    @Test
    @DisplayName("기존 질문 세트를 성공적으로 수정해야 한다")
    void testUpdateQuestionSet() throws Exception {
        QuestionSet existingSet = new QuestionSet("기존 세트", SurveyTopic.MOVIE_WATCHING);
        questionSetRepository.save(existingSet);

        String updatedName = "수정된 테스트 세트";
        SurveyTopic updatedTopic = SurveyTopic.HEALTH_WELLNESS;

        mockMvc.perform(post("/admin/question-sets/{id}/edit", existingSet.getId())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", updatedName)
                        .param("topic", updatedTopic.name()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/question-sets"));

        QuestionSet foundSet = questionSetRepository.findById(existingSet.getId()).orElse(null);
        assertThat(foundSet).isNotNull();
        assertThat(foundSet.getName()).isEqualTo(updatedName);
        assertThat(foundSet.getTopic()).isEqualTo(updatedTopic);
    }

    @Test
    @DisplayName("질문 세트를 성공적으로 논리적 삭제해야 한다")
    void testSoftDeleteQuestionSet() throws Exception {
        QuestionSet existingSet = new QuestionSet("삭제될 세트", SurveyTopic.MUSIC_LISTENING);
        questionSetRepository.save(existingSet);

        mockMvc.perform(post("/admin/question-sets/{id}/delete", existingSet.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/question-sets"));

        List<QuestionSet> activeSets = questionSetRepository.findAll();
        assertThat(activeSets).isEmpty();
    }
}
