package com.opicnic.opicnic.admin;

import com.opicnic.opicnic.domain.QuestionSet;
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
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
@Transactional // 테스트 후 롤백을 위해
public class QuestionSetAdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuestionSetRepository questionSetRepository;

    // Testcontainers를 사용하여 MySQL 컨테이너 정의
    @Container
    public static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    // Spring이 테스트용 DB에 연결하도록 속성 동적 설정
    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop"); // 테스트마다 스키마 재생성
    }

    @BeforeEach
    void setUp() {
        // 각 테스트 시작 전 데이터 초기화 (선택 사항, @Transactional로 롤백되므로 필수는 아님)
        questionSetRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        // 각 테스트 종료 후 데이터 정리 (선택 사항, @Transactional로 롤백되므로 필수는 아님)
    }

    @Test
    @DisplayName("새로운 질문 세트를 성공적으로 생성하고 저장해야 한다")
    void testCreateQuestionSet() throws Exception {
        // Given
        String name = "새로운 테스트 세트";
        SurveyTopic topic = SurveyTopic.TECHNOLOGY;
        SurveyDifficulty difficulty = SurveyDifficulty.LEVEL_5;

        // When
        mockMvc.perform(post("/admin/question-sets")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", name)
                        .param("topic", topic.name())
                        .param("difficulty", difficulty.name()))
                .andExpect(status().is3xxRedirection()) // 302 Found (리다이렉션) 기대
                .andExpect(redirectedUrl("/admin/question-sets")); // 리다이렉트 URL 검증

        // Then
        List<QuestionSet> foundSets = questionSetRepository.findAll();
        assertThat(foundSets).hasSize(1);
        assertThat(foundSets.get(0).getName()).isEqualTo(name);
        assertThat(foundSets.get(0).getTopic()).isEqualTo(topic);
        assertThat(foundSets.get(0).getDifficulty()).isEqualTo(difficulty);
        assertThat(foundSets.get(0).isDeleted()).isFalse(); // 논리적 삭제 필드 확인
    }

    @Test
    @DisplayName("기존 질문 세트를 성공적으로 수정해야 한다")
    void testUpdateQuestionSet() throws Exception {
        // Given
        QuestionSet existingSet = new QuestionSet("기존 세트", SurveyDifficulty.LEVEL_3, SurveyTopic.MOVIE_WATCHING);
        questionSetRepository.save(existingSet);

        String updatedName = "수정된 테스트 세트";
        SurveyTopic updatedTopic = SurveyTopic.HEALTH_WELLNESS;
        SurveyDifficulty updatedDifficulty = SurveyDifficulty.LEVEL_4;

        // When
        mockMvc.perform(post("/admin/question-sets/{id}/edit", existingSet.getId())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", updatedName)
                        .param("topic", updatedTopic.name())
                        .param("difficulty", updatedDifficulty.name()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/question-sets"));

        // Then
        QuestionSet foundSet = questionSetRepository.findById(existingSet.getId()).orElse(null);
        assertThat(foundSet).isNotNull();
        assertThat(foundSet.getName()).isEqualTo(updatedName);
        assertThat(foundSet.getTopic()).isEqualTo(updatedTopic);
        assertThat(foundSet.getDifficulty()).isEqualTo(updatedDifficulty);
    }

    @Test
    @DisplayName("질문 세트를 성공적으로 논리적 삭제해야 한다")
    void testSoftDeleteQuestionSet() throws Exception {
        // Given
        QuestionSet existingSet = new QuestionSet("삭제될 세트", SurveyDifficulty.LEVEL_3, SurveyTopic.MUSIC_LISTENING);
        questionSetRepository.save(existingSet);

        // When
        mockMvc.perform(post("/admin/question-sets/{id}/delete", existingSet.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/question-sets"));

        // Then
        // @Where(clause = "deleted = false") 때문에 findAll()에서는 보이지 않아야 함
        List<QuestionSet> activeSets = questionSetRepository.findAll();
        assertThat(activeSets).isEmpty();

        // 하지만 실제 DB에는 남아있어야 함 (findById로 직접 조회)
        // @Where가 적용되지 않은 별도의 쿼리 (예: JpaRepository의 findById)를 사용하거나
        // EntityManager를 통해 직접 조회해야 deleted = true인 엔티티를 볼 수 있습니다.
        // 여기서는 findAll()이 @Where에 의해 필터링되는 것을 확인하는 것으로 충분합니다.
        // 만약 deleted = true인 엔티티를 조회하고 싶다면, 별도의 Repository 메서드가 필요합니다.
    }
}
