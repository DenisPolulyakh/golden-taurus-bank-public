package ru.money.goldentaurusbank.www.backend.integrationtests;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.response.CategoryResponse;
import ru.money.goldentaurusbank.www.backend.repository.CategoryRepository;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
@DisplayName("Интеграционные тесты категорий")
class CategoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig()
                    .withUser("test@greenmail.com", "test"))
            .withPerMethodLifecycle(false);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.default-schema", () -> "taurus");
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());
        registry.add("spring.mail.username", () -> "test@greenmail.com");
        registry.add("spring.mail.password", () -> "test");
        registry.add("spring.mail.protocol", () -> "smtp");
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "true");
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.ssl.enable", () -> "false");
        registry.add("app.jwt.secret", () -> "testSecretKeyForJWTTokenGeneration2026");
        registry.add("app.jwt.access-expiration", () -> "3600000");
        registry.add("app.jwt.refresh-expiration", () -> "604800000");
    }

    private String accessToken;
    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        categoryRepository.deleteAll();
        
        // Регистрация и подтверждение пользователя
        String registerRequest = """
                {
                    "email": "categoryuser@example.com",
                    "password": "Test123%",
                    "fullName": "Category Test User"
                }
                """;
        
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));
        
        User user = userRepository.findByEmail("categoryuser@example.com").get();
        userId = user.getId();
        
        // Подтверждение email
        mockMvc.perform(get("/api/auth/verify")
                .param("token", user.getVerificationToken()));
        
        // Логин для получения токена
        String loginRequest = """
                {
                    "email": "categoryuser@example.com",
                    "password": "Test123%"
                }
                """;
        
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andReturn();
        
        String responseBody = loginResult.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        accessToken = jsonNode.get("data").get("token").asText();
    }

    @Test
    @DisplayName("Создание категории - успешно")
    void createCategorySuccess() throws Exception {
        String request = """
                {
                    "name": "Продукты"
                }
                """;

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Категория успешно добавлена"))
                .andExpect(jsonPath("$.data.name").value("Продукты"));

        // Проверяем, что категория сохранилась в БД
        List<CategoryResponse> categories = getCategories();
        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).getName()).isEqualTo("Продукты");
    }

    @Test
    @DisplayName("Создание категории с дублирующимся именем (без учёта регистра) - не создаёт дубль")
    void createCategoryDuplicateNameDoesNotCreateDuplicate() throws Exception {
        String request = """
                {
                    "name": "Транспорт"
                }
                """;

        // Первое создание
        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());

        // Второе создание с таким же именем (другой регистр)
        String requestDuplicate = """
                {
                    "name": "транспорт"
                }
                """;

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestDuplicate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Категория успешно добавлена"));

        // Проверяем, что в БД только одна категория
        List<CategoryResponse> categories = getCategories();
        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).getName()).isEqualTo("Транспорт");
    }

    @Test
    @DisplayName("Создание категории с пустым именем - ошибка валидации")
    void createCategoryEmptyNameValidationError() throws Exception {
        String request = """
                {
                    "name": ""
                }
                """;

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    @DisplayName("Получение всех категорий пользователя")
    void getAllCategoriesSuccess() throws Exception {
        // Создаём несколько категорий
        createCategory("Продукты");
        createCategory("Транспорт");
        createCategory("Развлечения");

        MvcResult result = mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        List<CategoryResponse> categories = objectMapper.convertValue(
                jsonNode.get("data"),
                new TypeReference<List<CategoryResponse>>() {}
        );
        
        assertThat(categories).extracting(CategoryResponse::getName)
                .containsExactlyInAnyOrder("Продукты", "Транспорт", "Развлечения");
    }

    @Test
    @DisplayName("Получение категорий у разных пользователей - изолированы")
    void getAllCategoriesUserIsolation() throws Exception {
        // Создаём категорию для первого пользователя
        createCategory("Категория Пользователя 1");
        
        // Создаём второго пользователя
        String registerRequest2 = """
                {
                    "email": "user2@example.com",
                    "password": "Test123%",
                    "fullName": "Second User"
                }
                """;
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest2));
        
        User user2 = userRepository.findByEmail("user2@example.com").get();
        mockMvc.perform(get("/api/auth/verify")
                .param("token", user2.getVerificationToken()));
        
        String loginRequest2 = """
                {
                    "email": "user2@example.com",
                    "password": "Test123%"
                }
                """;
        MvcResult loginResult2 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest2))
                .andReturn();
        
        String responseBody2 = loginResult2.getResponse().getContentAsString();
        JsonNode jsonNode2 = objectMapper.readTree(responseBody2);
        String tokenUser2 = jsonNode2.get("data").get("token").asText();
        
        // Создаём категорию для второго пользователя
        String requestCategory2 = """
                {
                    "name": "Категория Пользователя 2"
                }
                """;
        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + tokenUser2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestCategory2))
                .andExpect(status().isOk());
        
        // Проверяем категории первого пользователя
        List<CategoryResponse> categoriesUser1 = getCategories();
        assertThat(categoriesUser1).hasSize(1);
        assertThat(categoriesUser1.get(0).getName()).isEqualTo("Категория Пользователя 1");
        
        // Проверяем категории второго пользователя
        MvcResult resultUser2 = mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + tokenUser2))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseUser2 = resultUser2.getResponse().getContentAsString();
        JsonNode jsonNodeUser2 = objectMapper.readTree(responseUser2);
        List<CategoryResponse> categoriesUser2 = objectMapper.convertValue(
                jsonNodeUser2.get("data"),
                new TypeReference<List<CategoryResponse>>() {}
        );
        
        assertThat(categoriesUser2).hasSize(1);
        assertThat(categoriesUser2.get(0).getName()).isEqualTo("Категория Пользователя 2");
    }

    @Test
    @DisplayName("Обновление категории - успешно")
    void updateCategorySuccess() throws Exception {
        // Создаём категорию
        String createRequest = """
                {
                    "name": "Старое название"
                }
                """;
        
        MvcResult createResult = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andReturn();
        
        String createResponse = createResult.getResponse().getContentAsString();
        JsonNode createJson = objectMapper.readTree(createResponse);
        Long categoryId = createJson.get("data").get("id").asLong();
        
        // Обновляем категорию
        String updateRequest = """
                {
                    "name": "Новое название"
                }
                """;
        
        mockMvc.perform(put("/api/categories/{categoryId}", categoryId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Категория успешно обновлена"))
                .andExpect(jsonPath("$.data.name").value("Новое название"));
        
        // Проверяем в БД
        List<CategoryResponse> categories = getCategories();
        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).getName()).isEqualTo("Новое название");
    }

    @Test
    @DisplayName("Обновление категории на существующее имя (без учёта регистра) - не обновляет")
    void updateCategoryToExistingNameDoesNotUpdate() throws Exception {
        // Создаём две категории
        createCategory("Еда");
        String createRequest2 = """
                {
                    "name": "Транспорт"
                }
                """;
        
        MvcResult createResult2 = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest2))
                .andReturn();
        
        String createResponse2 = createResult2.getResponse().getContentAsString();
        JsonNode createJson2 = objectMapper.readTree(createResponse2);
        Long transportId = createJson2.get("data").get("id").asLong();
        
        // Пытаемся обновить "Транспорт" на "ЕДА" (существующее имя)
        String updateRequest = """
                {
                    "name": "ЕДА"
                }
                """;
        
        mockMvc.perform(put("/api/categories/{categoryId}", transportId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Категория успешно обновлена"));
        
        // Проверяем, что имя не изменилось
        List<CategoryResponse> categories = getCategories();
        assertThat(categories).extracting(CategoryResponse::getName)
                .containsExactlyInAnyOrder("Еда", "Транспорт");
    }

    @Test
    @DisplayName("Обновление несуществующей категории - ошибка")
    void updateCategoryNotFoundThrowsException() throws Exception {
        String updateRequest = """
                {
                    "name": "Новое имя"
                }
                """;
        
        mockMvc.perform(put("/api/categories/{categoryId}", 99999L)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4003));
    }

    @Test
    @DisplayName("Удаление категории - успешно")
    void deleteCategorySuccess() throws Exception {
        // Создаём категорию
        String createRequest = """
                {
                    "name": "Удаляемая категория"
                }
                """;
        
        MvcResult createResult = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andReturn();
        
        String createResponse = createResult.getResponse().getContentAsString();
        JsonNode createJson = objectMapper.readTree(createResponse);
        Long categoryId = createJson.get("data").get("id").asLong();
        
        // Проверяем, что категория создалась
        assertThat(getCategories()).hasSize(1);
        
        // Удаляем категорию
        mockMvc.perform(delete("/api/categories/{categoryId}", categoryId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Категория успешно удалена"));
        
        // Проверяем, что категория удалилась
        assertThat(getCategories()).isEmpty();
    }

    @Test
    @DisplayName("Удаление несуществующей категории - ошибка")
    void deleteCategoryNotFoundThrowsException() throws Exception {
        mockMvc.perform(delete("/api/categories/{categoryId}", 99999L)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4003));
    }

    @Test
    @DisplayName("Удаление категории другого пользователя - ошибка")
    void deleteCategoryAnotherUserThrowsException() throws Exception {
        // Создаём второго пользователя
        String registerRequest2 = """
                {
                    "email": "another@example.com",
                    "password": "Test123%",
                    "fullName": "Another User"
                }
                """;
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest2));
        
        User anotherUser = userRepository.findByEmail("another@example.com").get();
        mockMvc.perform(get("/api/auth/verify")
                .param("token", anotherUser.getVerificationToken()));
        
        String loginRequest2 = """
                {
                    "email": "another@example.com",
                    "password": "Test123%"
                }
                """;
        MvcResult loginResult2 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest2))
                .andReturn();
        
        String responseBody2 = loginResult2.getResponse().getContentAsString();
        JsonNode jsonNode2 = objectMapper.readTree(responseBody2);
        String tokenUser2 = jsonNode2.get("data").get("token").asText();
        
        // Создаём категорию для второго пользователя
        String createRequest2 = """
                {
                    "name": "Категория другого пользователя"
                }
                """;
        MvcResult createResult2 = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + tokenUser2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest2))
                .andReturn();
        
        String createResponse2 = createResult2.getResponse().getContentAsString();
        JsonNode createJson2 = objectMapper.readTree(createResponse2);
        Long anotherCategoryId = createJson2.get("data").get("id").asLong();
        
        // Пытаемся удалить категорию второго пользователя первым пользователем
        mockMvc.perform(delete("/api/categories/{categoryId}", anotherCategoryId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4003));
        
        // Проверяем, что категория второго пользователя осталась
        MvcResult resultUser2 = mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + tokenUser2))
                .andReturn();
        
        String responseUser2 = resultUser2.getResponse().getContentAsString();
        JsonNode jsonNodeUser2 = objectMapper.readTree(responseUser2);
        List<CategoryResponse> categoriesUser2 = objectMapper.convertValue(
                jsonNodeUser2.get("data"),
                new TypeReference<List<CategoryResponse>>() {}
        );
        
        assertThat(categoriesUser2).hasSize(1);
    }

    @Test
    @DisplayName("Попытка доступа к категориям без токена - ошибка")
    void accessWithoutTokenThrowsException() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isForbidden());
    }

    // Вспомогательные методы
    private void createCategory(String name) throws Exception {
        String request = String.format("""
                {
                    "name": "%s"
                }
                """, name);
        
        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
    }

    private List<CategoryResponse> getCategories() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn();
        
        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        return objectMapper.convertValue(
                jsonNode.get("data"),
                new TypeReference<List<CategoryResponse>>() {}
        );
    }
}