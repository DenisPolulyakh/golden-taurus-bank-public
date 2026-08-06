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
import ru.money.goldentaurusbank.www.backend.model.dto.response.BullionNameResponse;
import ru.money.goldentaurusbank.www.backend.repository.BullionNameRepository;
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
@DisplayName("Интеграционные тесты наименований слитков")
class BullionNameIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BullionNameRepository bullionNameRepository;

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
        bullionNameRepository.deleteAll();
        
        // Регистрация и подтверждение пользователя
        String registerRequest = """
                {
                    "email": "bullionNameuser@example.com",
                    "password": "Test123%",
                    "fullName": "Bullion Test User"
                }
                """;
        
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));
        
        User user = userRepository.findByEmail("bullionNameuser@example.com").get();
        userId = user.getId();
        
        // Подтверждение email
        mockMvc.perform(get("/api/auth/verify")
                .param("token", user.getVerificationToken()));
        
        // Логин для получения токена
        String loginRequest = """
                {
                    "email": "bullionNameuser@example.com",
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
    void createBullionNameSuccess() throws Exception {
        String request = """
                {
                    "title": "Продукты"
                }
                """;

        mockMvc.perform(post("/api/bullion-names")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Наименование слитка успешно добавлено"))
                .andExpect(jsonPath("$.data.title").value("Продукты"));

        // Проверяем, что категория сохранилась в БД
        List<BullionNameResponse> bullionNames = getBullionNames();
        assertThat(bullionNames).hasSize(1);
        assertThat(bullionNames.get(0).getTitle()).isEqualTo("Продукты");
    }

    @Test
    @DisplayName("Создание категории с дублирующимся именем (без учёта регистра) - не создаёт дубль")
    void createBullionNameDuplicateNameDoesNotCreateDuplicate() throws Exception {
        String request = """
                {
                    "title": "Транспорт"
                }
                """;

        // Первое создание
        mockMvc.perform(post("/api/bullion-names")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());

        // Второе создание с таким же именем (другой регистр)
        String requestDuplicate = """
                {
                    "title": "транспорт"
                }
                """;

        mockMvc.perform(post("/api/bullion-names")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestDuplicate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Наименование слитка успешно добавлено"));

        // Проверяем, что в БД только одна категория
        List<BullionNameResponse> bullionNames = getBullionNames();
        assertThat(bullionNames).hasSize(1);
        assertThat(bullionNames.get(0).getTitle()).isEqualTo("Транспорт");
    }

    @Test
    @DisplayName("Создание категории с пустым именем - ошибка валидации")
    void createBullionNameEmptyNameValidationError() throws Exception {
        String request = """
                {
                    "title": ""
                }
                """;

        mockMvc.perform(post("/api/bullion-names")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    @DisplayName("Получение всех категорий пользователя")
    void getAllBullionNamesSuccess() throws Exception {
        // Создаём несколько категорий
        createBullionName("Продукты");
        createBullionName("Транспорт");
        createBullionName("Развлечения");

        MvcResult result = mockMvc.perform(get("/api/bullion-names")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        List<BullionNameResponse> bullionNames = objectMapper.convertValue(
                jsonNode.get("data"),
                new TypeReference<List<BullionNameResponse>>() {}
        );
        
        assertThat(bullionNames).extracting(BullionNameResponse::getName)
                .containsExactlyInAnyOrder("Продукты", "Транспорт", "Развлечения");
    }

    @Test
    @DisplayName("Получение категорий у разных пользователей - изолированы")
    void getAllBullionNamesUserIsolation() throws Exception {
        // Создаём категорию для первого пользователя
        createBullionName("Категория Пользователя 1");
        
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
        String requestBullionName2 = """
                {
                    "title": "Категория Пользователя 2"
                }
                """;
        mockMvc.perform(post("/api/bullion-names")
                        .header("Authorization", "Bearer " + tokenUser2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBullionName2))
                .andExpect(status().isOk());
        
        // Проверяем категории первого пользователя
        List<BullionNameResponse> bullionNamesUser1 = getBullionNames();
        assertThat(bullionNamesUser1).hasSize(1);
        assertThat(bullionNamesUser1.get(0).getTitle()).isEqualTo("Категория Пользователя 1");
        
        // Проверяем категории второго пользователя
        MvcResult resultUser2 = mockMvc.perform(get("/api/bullion-names")
                        .header("Authorization", "Bearer " + tokenUser2))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseUser2 = resultUser2.getResponse().getContentAsString();
        JsonNode jsonNodeUser2 = objectMapper.readTree(responseUser2);
        List<BullionNameResponse> bullionNamesUser2 = objectMapper.convertValue(
                jsonNodeUser2.get("data"),
                new TypeReference<List<BullionNameResponse>>() {}
        );
        
        assertThat(bullionNamesUser2).hasSize(1);
        assertThat(bullionNamesUser2.get(0).getTitle()).isEqualTo("Категория Пользователя 2");
    }

    @Test
    @DisplayName("Обновление категории - успешно")
    void updateBullionNameSuccess() throws Exception {
        // Создаём категорию
        String createRequest = """
                {
                    "title": "Старое название"
                }
                """;
        
        MvcResult createResult = mockMvc.perform(post("/api/bullion-names")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andReturn();
        
        String createResponse = createResult.getResponse().getContentAsString();
        JsonNode createJson = objectMapper.readTree(createResponse);
        Long bullionNameId = createJson.get("data").get("id").asLong();
        
        // Обновляем категорию
        String updateRequest = """
                {
                    "title": "Новое название"
                }
                """;
        
        mockMvc.perform(put("/api/bullion-names/{bullionNameId}", bullionNameId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Наименование слитка успешно обновлено"))
                .andExpect(jsonPath("$.data.title").value("Новое название"));
        
        // Проверяем в БД
        List<BullionNameResponse> bullionNames = getBullionNames();
        assertThat(bullionNames).hasSize(1);
        assertThat(bullionNames.get(0).getTitle()).isEqualTo("Новое название");
    }

    @Test
    @DisplayName("Обновление категории на существующее имя (без учёта регистра) - не обновляет")
    void updateBullionNameToExistingNameDoesNotUpdate() throws Exception {
        // Создаём две категории
        createBullionName("Еда");
        String createRequest2 = """
                {
                    "title": "Транспорт"
                }
                """;
        
        MvcResult createResult2 = mockMvc.perform(post("/api/bullion-names")
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
                    "title": "ЕДА"
                }
                """;
        
        mockMvc.perform(put("/api/bullion-names/{bullionNameId}", transportId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Наименование слитка успешно обновлено"));
        
        // Проверяем, что имя не изменилось
        List<BullionNameResponse> bullionNames = getBullionNames();
        assertThat(bullionNames).extracting(BullionNameResponse::getName)
                .containsExactlyInAnyOrder("Еда", "Транспорт");
    }

    @Test
    @DisplayName("Обновление несуществующей категории - ошибка")
    void updateBullionNameNotFoundThrowsException() throws Exception {
        String updateRequest = """
                {
                    "title": "Новое имя"
                }
                """;
        
        mockMvc.perform(put("/api/bullion-names/{bullionNameId}", 99999L)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4003));
    }

    @Test
    @DisplayName("Удаление категории - успешно")
    void deleteBullionNameSuccess() throws Exception {
        // Создаём категорию
        String createRequest = """
                {
                    "title": "Удаляемая категория"
                }
                """;
        
        MvcResult createResult = mockMvc.perform(post("/api/bullion-names")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andReturn();
        
        String createResponse = createResult.getResponse().getContentAsString();
        JsonNode createJson = objectMapper.readTree(createResponse);
        Long bullionNameId = createJson.get("data").get("id").asLong();
        
        // Проверяем, что категория создалась
        assertThat(getBullionNames()).hasSize(1);
        
        // Удаляем категорию
        mockMvc.perform(delete("/api/bullion-names/{bullionNameId}", bullionNameId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Наименование слитка успешно удалено"));
        
        // Проверяем, что категория удалилась
        assertThat(getBullionNames()).isEmpty();
    }

    @Test
    @DisplayName("Удаление несуществующей категории - ошибка")
    void deleteBullionNameNotFoundThrowsException() throws Exception {
        mockMvc.perform(delete("/api/bullion-names/{bullionNameId}", 99999L)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4003));
    }

    @Test
    @DisplayName("Удаление категории другого пользователя - ошибка")
    void deleteBullionNameAnotherUserThrowsException() throws Exception {
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
                    "title": "Категория другого пользователя"
                }
                """;
        MvcResult createResult2 = mockMvc.perform(post("/api/bullion-names")
                        .header("Authorization", "Bearer " + tokenUser2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest2))
                .andReturn();
        
        String createResponse2 = createResult2.getResponse().getContentAsString();
        JsonNode createJson2 = objectMapper.readTree(createResponse2);
        Long anotherBullionNameId = createJson2.get("data").get("id").asLong();
        
        // Пытаемся удалить категорию второго пользователя первым пользователем
        mockMvc.perform(delete("/api/bullion-names/{bullionNameId}", anotherBullionNameId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4003));
        
        // Проверяем, что категория второго пользователя осталась
        MvcResult resultUser2 = mockMvc.perform(get("/api/bullion-names")
                        .header("Authorization", "Bearer " + tokenUser2))
                .andReturn();
        
        String responseUser2 = resultUser2.getResponse().getContentAsString();
        JsonNode jsonNodeUser2 = objectMapper.readTree(responseUser2);
        List<BullionNameResponse> bullionNamesUser2 = objectMapper.convertValue(
                jsonNodeUser2.get("data"),
                new TypeReference<List<BullionNameResponse>>() {}
        );
        
        assertThat(bullionNamesUser2).hasSize(1);
    }

    @Test
    @DisplayName("Попытка доступа к категориям без токена - ошибка")
    void accessWithoutTokenThrowsException() throws Exception {
        mockMvc.perform(get("/api/bullion-names"))
                .andExpect(status().isForbidden());
    }

    // Вспомогательные методы
    private void createBullionName(String name) throws Exception {
        String request = String.format("""
                {
                    "title": "%s"
                }
                """, name);
        
        mockMvc.perform(post("/api/bullion-names")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
    }

    private List<BullionNameResponse> getBullionNames() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/bullion-names")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn();
        
        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        return objectMapper.convertValue(
                jsonNode.get("data"),
                new TypeReference<List<BullionNameResponse>>() {}
        );
    }
}