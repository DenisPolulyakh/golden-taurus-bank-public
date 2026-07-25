package ru.money.goldentaurusbank.www.backend.integrationtests;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.persistence.EntityManager;
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
import ru.money.goldentaurusbank.www.backend.model.dto.response.GroupedBullionResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.VaultSummaryResponse;
import ru.money.goldentaurusbank.www.backend.repository.BullionRepository;
import ru.money.goldentaurusbank.www.backend.repository.CategoryRepository;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;
import ru.money.goldentaurusbank.www.backend.repository.VaultRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
@DisplayName("Интеграционные тесты слитков")
class CategoryBullionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private VaultRepository vaultRepository;

    @Autowired
    private BullionRepository bullionRepository;


    @Autowired
    private EntityManager entityManager;

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
    private Long categoryId1;
    private Long categoryId2;
    private Long vaultId1;
    private Long vaultId2;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        categoryRepository.deleteAll();
        vaultRepository.deleteAll();
        bullionRepository.deleteAll();

        String registerRequest = """
                {
                    "email": "bullionuser@example.com",
                    "password": "Test123%",
                    "fullName": "Bullion Test User"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));

        User user = userRepository.findByEmail("bullionuser@example.com").get();
        userId = user.getId();

        mockMvc.perform(get("/api/auth/verify")
                .param("token", user.getVerificationToken()));

        String loginRequest = """
                {
                    "email": "bullionuser@example.com",
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

        String createCategoryRequest1 = """
                {
                    "name": "Финансовая подушка"
                }
                """;
        MvcResult categoryResult1 = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCategoryRequest1))
                .andReturn();
        categoryId1 = objectMapper.readTree(categoryResult1.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String createCategoryRequest2 = """
                {
                    "name": "Накопления"
                }
                """;
        MvcResult categoryResult2 = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCategoryRequest2))
                .andReturn();
        categoryId2 = objectMapper.readTree(categoryResult2.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String createVaultRequest1 = """
                {
                    "name": "Сбербанк",
                    "interestRate": 5.0
                }
                """;
        MvcResult vaultResult1 = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createVaultRequest1))
                .andReturn();
        vaultId1 = objectMapper.readTree(vaultResult1.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String createVaultRequest2 = """
                {
                    "name": "Тинькофф",
                    "interestRate": 6.0
                }
                """;
        MvcResult vaultResult2 = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createVaultRequest2))
                .andReturn();
        vaultId2 = objectMapper.readTree(vaultResult2.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    @Test
    @DisplayName("Создание слитка - успешно")
    void createBullionSuccess() throws Exception {
        String request = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 100000,
                    "description": "На чёрный день",
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(categoryId1, vaultId1);

        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Слиток успешно добавлен"))
                .andExpect(jsonPath("$.data.amount").value(100000));
    }

    @Test
    @DisplayName("Создание слитка с дубликатом категории и хранилища - обновляет сумму")
    void createBullionDuplicateUpdatesAmount() throws Exception {
        String request1 = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 100000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(categoryId1, vaultId1);

        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request1))
                .andExpect(status().isOk());

        String request2 = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 50000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(categoryId1, vaultId1);

        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Слиток успешно добавлен"))
                .andExpect(jsonPath("$.data.amount").value(150000));
    }

    @Test
    @DisplayName("Получение группированных слитков - успешно")
    void getGroupedBullionsSuccess() throws Exception {
        String createBullion1 = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 100000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(categoryId1, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion1))
                .andExpect(status().isOk());

        String createBullion2 = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 50000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(categoryId1, vaultId2);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion2))
                .andExpect(status().isOk());

        String createBullion3 = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 75000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(categoryId2, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion3))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        MvcResult result = mockMvc.perform(get("/api/bullions/grouped")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalAmount").exists())
                .andExpect(jsonPath("$.data.totalAmount").value(225000))
                .andExpect(jsonPath("$.data.averageRate").value(5.22))
                .andExpect(jsonPath("$.data.categoryBullionList").isArray())
                .andExpect(jsonPath("$.data.categoryBullionList.length()").value(2))
                .andReturn();


        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);

        // Получаем общую статистику
        BigDecimal totalAmount = new BigDecimal(jsonNode.get("data").get("totalAmount").asText());
        BigDecimal averageRate = new BigDecimal(jsonNode.get("data").get("averageRate").asText());

        // Получаем список категорий
        List<GroupedBullionResponse.CategoryBullion> categoryBullionList = objectMapper.convertValue(
                jsonNode.get("data").get("categoryBullionList"),
                new TypeReference<List<GroupedBullionResponse.CategoryBullion>>() {}
        );

        // Проверяем общую сумму
        assertThat(totalAmount).isEqualByComparingTo(new BigDecimal("225000"));

        // Проверяем первую категорию
        GroupedBullionResponse.CategoryBullion group1 = categoryBullionList.stream()
                .filter(g -> g.getCategoryId().equals(categoryId1))
                .findFirst()
                .orElse(null);

        assertThat(group1).isNotNull();
        assertThat(group1.getCategoryName()).isNotEmpty();
        assertThat(group1.getCategoryAmount()).isEqualByComparingTo(new BigDecimal("150000"));
        assertThat(group1.getCategoryAverageRate()).isEqualByComparingTo(new BigDecimal("5.33"));
        assertThat(group1.getVaults()).hasSize(2);

        // Проверяем хранилища в первой категории
        GroupedBullionResponse.CategoryBullion.VaultInfo vault1 = group1.getVaults().stream()
                .filter(v -> v.getId().equals(vaultId1))
                .findFirst()
                .orElse(null);
        assertThat(vault1).isNotNull();
        assertThat(vault1.getAmount()).isEqualByComparingTo(new BigDecimal("100000"));

        GroupedBullionResponse.CategoryBullion.VaultInfo vault2 = group1.getVaults().stream()
                .filter(v -> v.getId().equals(vaultId2))
                .findFirst()
                .orElse(null);
        assertThat(vault2).isNotNull();
        assertThat(vault2.getAmount()).isEqualByComparingTo(new BigDecimal("50000"));

        // Проверяем вторую категорию
        GroupedBullionResponse.CategoryBullion group2 = categoryBullionList.stream()
                .filter(g -> g.getCategoryId().equals(categoryId2))
                .findFirst()
                .orElse(null);

        assertThat(group2).isNotNull();
        assertThat(group2.getCategoryName()).isNotEmpty();
        assertThat(group2.getCategoryAmount()).isEqualByComparingTo(new BigDecimal("75000"));
        assertThat(group2.getVaults()).hasSize(1);

        // Проверяем хранилище во второй категории
        GroupedBullionResponse.CategoryBullion.VaultInfo vaultInGroup2 = group2.getVaults().stream()
                .filter(v -> v.getId().equals(vaultId1))
                .findFirst()
                .orElse(null);
        assertThat(vaultInGroup2).isNotNull();
        assertThat(vaultInGroup2.getAmount()).isEqualByComparingTo(new BigDecimal("75000"));


    }

    @Test
    @DisplayName("Получение сводки по хранилищу - успешно")
    void getVaultSummarySuccess() throws Exception {
        String createBullion1 = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 100000,
                    "description": "Описание 1",
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(categoryId1, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion1))
                .andExpect(status().isOk());

        String createBullion2 = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 75000,
                    "description": "Описание 2",
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(categoryId2, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion2))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        MvcResult result = mockMvc.perform(get("/api/vaults/{vaultId}/summary", vaultId1)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.vaultId").value(vaultId1))
                .andExpect(jsonPath("$.data.vaultName").value("Сбербанк"))
                .andExpect(jsonPath("$.data.totalAmount").value(175000))
                .andExpect(jsonPath("$.data.categoriesCount").value(2))
                .andExpect(jsonPath("$.data.bullions").isArray())
                .andExpect(jsonPath("$.data.bullions.length()").value(2))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        VaultSummaryResponse summary = objectMapper.convertValue(
                jsonNode.get("data"),
                new TypeReference<VaultSummaryResponse>() {}
        );

        assertThat(summary.getTotalAmount()).isEqualByComparingTo(new BigDecimal("175000"));
        assertThat(summary.getBullions()).hasSize(2);
    }

    @Test
    @DisplayName("Получение сводки по несуществующему хранилищу - ошибка")
    void getVaultSummaryNotFoundThrowsException() throws Exception {
        mockMvc.perform(get("/api/vaults/{vaultId}/summary", 99999L)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4004));
    }

    @Test
    @DisplayName("Получение общей суммы всех слитков")
    void getTotalAmountSuccess() throws Exception {
        String createBullion1 = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 100000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(categoryId1, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion1))
                .andExpect(status().isOk());

        String createBullion2 = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 50000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(categoryId2, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion2))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bullions/total")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(150000));
    }

    @Test
    @DisplayName("Получение всех слитков пользователя")
    void getAllBullionsSuccess() throws Exception {
        String createBullion1 = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 100000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(categoryId1, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion1))
                .andExpect(status().isOk());

        String createBullion2 = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 50000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(categoryId2, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion2))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("Обновление слитка - успешно")
    void updateBullionSuccess() throws Exception {
        String createRequest = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 100000,
                    "description": "Старое описание",
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(categoryId1, vaultId1);

        MvcResult createResult = mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andReturn();

        Long bullionId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String updateRequest = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 150000,
                    "description": "Новое описание",
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(categoryId1, vaultId1);

        mockMvc.perform(put("/api/bullions/{bullionId}", bullionId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.amount").value(150000))
                .andExpect(jsonPath("$.data.description").value("Новое описание"));
    }

    @Test
    @DisplayName("Удаление слитка - успешно")
    void deleteBullionSuccess() throws Exception {
        String createRequest = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 100000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(categoryId1, vaultId1);

        MvcResult createResult = mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andReturn();

        Long bullionId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(delete("/api/bullions/{bullionId}", bullionId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Слиток успешно удалён"));
    }

    @Test
    @DisplayName("Попытка доступа к слиткам без токена - ошибка")
    void accessWithoutTokenThrowsException() throws Exception {
        mockMvc.perform(get("/api/bullions"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Получение группированных слитков - пустой список")
    void getGroupedBullionsEmpty() throws Exception {
        mockMvc.perform(get("/api/bullions/grouped")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.categoryBullionList").isArray())
                .andExpect(jsonPath("$.data.categoryBullionList.length()").value(0))
                .andReturn();
    }

    @Test
    @DisplayName("Получение группированных слитков - несколько категорий в одном хранилище")
    void getGroupedBullionsMultipleCategoriesOneVault() throws Exception {
        String createBullion1 = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 100000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(categoryId1, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion1))
                .andExpect(status().isOk());

        String createBullion2 = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 75000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(categoryId2, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion2))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        MvcResult result = mockMvc.perform(get("/api/bullions/grouped")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalAmount").exists())
                .andExpect(jsonPath("$.data.averageRate").exists())
                .andExpect(jsonPath("$.data.categoryBullionList").isArray())
                .andExpect(jsonPath("$.data.categoryBullionList.length()").value(2))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);

        // Получаем список категорий
        List<GroupedBullionResponse.CategoryBullion> categoryBullionList = objectMapper.convertValue(
                jsonNode.get("data").get("categoryBullionList"),
                new TypeReference<List<GroupedBullionResponse.CategoryBullion>>() {}
        );

        assertThat(categoryBullionList).hasSize(2);
        assertThat(categoryBullionList).extracting(GroupedBullionResponse.CategoryBullion::getCategoryName)
                .containsExactlyInAnyOrder("Финансовая подушка", "Накопления");

        // Проверяем, что у обоих категорий vaults содержит одно хранилище
        for (GroupedBullionResponse.CategoryBullion category : categoryBullionList) {
            assertThat(category.getVaults()).hasSize(1);
            assertThat(category.getVaults().get(0).getId()).isEqualTo(vaultId1);
            assertThat(category.getVaults().get(0).getName()).isNotEmpty();
        }

        // Проверяем сумму первой категории
        GroupedBullionResponse.CategoryBullion category1 = categoryBullionList.stream()
                .filter(c -> c.getCategoryId().equals(categoryId1))
                .findFirst()
                .orElse(null);
        assertThat(category1).isNotNull();
        assertThat(category1.getCategoryAmount()).isEqualByComparingTo(new BigDecimal("100000"));

        // Проверяем сумму второй категории
        GroupedBullionResponse.CategoryBullion category2 = categoryBullionList.stream()
                .filter(c -> c.getCategoryId().equals(categoryId2))
                .findFirst()
                .orElse(null);
        assertThat(category2).isNotNull();
        assertThat(category2.getCategoryAmount()).isEqualByComparingTo(new BigDecimal("75000"));

        // Проверяем общую сумму
        BigDecimal totalAmount = new BigDecimal(jsonNode.get("data").get("totalAmount").asText());
        assertThat(totalAmount).isEqualByComparingTo(new BigDecimal("175000"));
    }

    @Test
    @DisplayName("Получение хранилища - пустое хранилище")
    void getVaultSummaryEmpty() throws Exception {
        mockMvc.perform(get("/api/vaults/{vaultId}", vaultId1)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(vaultId1))
                .andExpect(jsonPath("$.data.name").value("Сбербанк"))
                .andExpect(jsonPath("$.data.totalAmount").value(0))
                .andExpect(jsonPath("$.data.categoriesCount").value(0));
    }

    @Test
    @DisplayName("Получение сводки по хранилищу с несколькими слитками")
    void getVaultSummaryMultipleBullions() throws Exception {
        String createBullion1 = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 100000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(categoryId1, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion1))
                .andExpect(status().isOk());

        String createBullion2 = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 50000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(categoryId1, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion2))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        MvcResult result = mockMvc.perform(get("/api/vaults/{vaultId}/summary", vaultId1)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(150000))
                .andExpect(jsonPath("$.data.categoriesCount").value(1))
                .andExpect(jsonPath("$.data.bullions.length()").value(1))
                .andExpect(jsonPath("$.data.bullions[0].amount").value(150000))
                .andReturn();
    }

    @Test
    @DisplayName("Получение сводки по хранилищу - несколько категорий")
    void getVaultSummaryMultipleCategories() throws Exception {
        String createBullion1 = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 100000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(categoryId1, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion1))
                .andExpect(status().isOk());

        String createBullion2 = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 75000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(categoryId2, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion2))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        MvcResult result = mockMvc.perform(get("/api/vaults/{vaultId}/summary", vaultId1)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(175000))
                .andExpect(jsonPath("$.data.categoriesCount").value(2))
                .andExpect(jsonPath("$.data.bullions.length()").value(2))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        VaultSummaryResponse summary = objectMapper.convertValue(
                jsonNode.get("data"),
                new TypeReference<VaultSummaryResponse>() {}
        );

        assertThat(summary.getBullions()).hasSize(2);
        assertThat(summary.getBullions()).extracting(VaultSummaryResponse.BullionByCategoryResponse::getCategoryName)
                .containsExactlyInAnyOrder("Финансовая подушка", "Накопления");
    }
}