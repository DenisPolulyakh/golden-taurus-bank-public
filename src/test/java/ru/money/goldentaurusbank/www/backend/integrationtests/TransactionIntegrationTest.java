package ru.money.goldentaurusbank.www.backend.integrationtests;

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
import ru.money.goldentaurusbank.www.backend.repository.*;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
@DisplayName("Интеграционные тесты транзакций")
class TransactionIntegrationTest {

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
    private TransactionLogRepository transactionLogRepository;

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
    private Long bullionId1;
    private Long bullionId2;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        categoryRepository.deleteAll();
        vaultRepository.deleteAll();
        bullionRepository.deleteAll();
        transactionLogRepository.deleteAll();

        // 1. Регистрация пользователя
        String registerRequest = """
                {
                    "email": "transactionuser@example.com",
                    "password": "Test123%",
                    "fullName": "Transaction Test User"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));

        User user = userRepository.findByEmail("transactionuser@example.com").get();
        userId = user.getId();

        mockMvc.perform(get("/api/auth/verify")
                .param("token", user.getVerificationToken()));

        String loginRequest = """
                {
                    "email": "transactionuser@example.com",
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

        // 2. Создаем категории
        String createCategory1 = """
                {
                    "name": "Финансовая подушка"
                }
                """;
        MvcResult catResult1 = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCategory1))
                .andReturn();
        categoryId1 = objectMapper.readTree(catResult1.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String createCategory2 = """
                {
                    "name": "Накопления"
                }
                """;
        MvcResult catResult2 = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCategory2))
                .andReturn();
        categoryId2 = objectMapper.readTree(catResult2.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // 3. Создаем хранилища
        String createVault1 = """
                {
                    "name": "Сбербанк"
                }
                """;
        MvcResult vaultResult1 = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createVault1))
                .andReturn();
        vaultId1 = objectMapper.readTree(vaultResult1.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String createVault2 = """
                {
                    "name": "Тинькофф"
                }
                """;
        MvcResult vaultResult2 = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createVault2))
                .andReturn();
        vaultId2 = objectMapper.readTree(vaultResult2.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // 4. Создаем слитки
        String createBullion1 = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 100000
                }
                """.formatted(categoryId1, vaultId1);
        MvcResult bullionResult1 = mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion1))
                .andReturn();
        bullionId1 = objectMapper.readTree(bullionResult1.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String createBullion2 = """
                {
                    "categoryId": %d,
                    "vaultId": %d,
                    "amount": 50000
                }
                """.formatted(categoryId2, vaultId2);
        MvcResult bullionResult2 = mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion2))
                .andReturn();
        bullionId2 = objectMapper.readTree(bullionResult2.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    // ==================== ТЕСТЫ СТАТИСТИКИ ====================

    @Test
    @DisplayName("Получение статистики дашборда - успешно")
    void getDashboardStatisticsSuccess() throws Exception {
        // Делаем несколько операций через существующие эндпоинты
        performRefill(50000);
        performRefill(30000);
        performWithdraw(20000);

        mockMvc.perform(get("/api/transactions/dashboard/statistics")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyData").isArray())
                .andExpect(jsonPath("$.totalIncome").exists())
                .andExpect(jsonPath("$.totalExpense").exists())
                .andExpect(jsonPath("$.netChange").exists())
                .andExpect(jsonPath("$.totalTransactions").exists())
                .andExpect(jsonPath("$.recentTransactions").isArray());
    }

    @Test
    @DisplayName("Получение статистики дашборда за конкретный год")
    void getDashboardStatisticsByYear() throws Exception {
        performRefill(100000);

        mockMvc.perform(get("/api/transactions/dashboard/statistics")
                        .param("year", "2024")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyData").isArray());
    }



    // ==================== ТЕСТЫ ИСТОРИИ ====================

    @Test
    @DisplayName("Получение истории транзакций - успешно")
    void getTransactionHistorySuccess() throws Exception {
        performRefill(100000);
        performRefill(50000);
        performWithdraw(30000);

        mockMvc.perform(get("/api/transactions/history")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].operationType").exists())
                .andExpect(jsonPath("$.content[0].amount").exists())
                .andExpect(jsonPath("$.content[0].status").exists())
                .andExpect(jsonPath("$.content[0].canRollback").exists());
    }

    @Test
    @DisplayName("Получение истории с фильтром по типу операции")
    void getTransactionHistoryFilterByType() throws Exception {
        performRefill(100000);
        performRefill(50000);
        performWithdraw(30000);

        mockMvc.perform(get("/api/transactions/history")
                        .param("operationType", "REFILL_BULLION")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].operationType").value("WITHDRAW_BULLION"));
    }

    @Test
    @DisplayName("Получение истории с пагинацией")
    void getTransactionHistoryWithPagination() throws Exception {
        for (int i = 0; i < 10; i++) {
            performRefill(10000 + i * 1000);
        }

        mockMvc.perform(get("/api/transactions/history")
                        .param("page", "0")
                        .param("size", "5")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.currentPage").value(0));
    }

    @Test
    @DisplayName("Получение доступных годов для фильтрации")
    void getAvailableYears() throws Exception {
        performRefill(100000);

        mockMvc.perform(get("/api/transactions/available-years")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ==================== ТЕСТЫ ОТКАТА ====================

    @Test
    @DisplayName("Откат последней транзакции - успешно")
    void rollbackLastTransactionSuccess() throws Exception {
        // Получаем начальную сумму
        BigDecimal initialAmount = getBullionAmount(bullionId1);

        // Создаем транзакцию
        performRefill(50000);
        BigDecimal afterRefill = getBullionAmount(bullionId1);
        assertThat(afterRefill).isGreaterThan(initialAmount);

        // Откатываем последнюю
        mockMvc.perform(post("/api/transactions/rollback-last")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // Проверяем, что сумма вернулась
        BigDecimal afterRollback = getBullionAmount(bullionId1);
        assertThat(afterRollback).isEqualByComparingTo(initialAmount);
    }

    @Test
    @DisplayName("Откат конкретной транзакции по ID - успешно")
    void rollbackTransactionByIdSuccess() throws Exception {
        // Создаем транзакцию и получаем её ID
        Long transactionId = getLastTransactionId();
        BigDecimal beforeRollback = getBullionAmount(bullionId1);

        // Откатываем
        mockMvc.perform(post("/api/transactions/{id}/rollback", transactionId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // Проверяем
        BigDecimal afterRollback = getBullionAmount(bullionId1);
        assertThat(afterRollback).isLessThan(beforeRollback);
    }

    @Test
    @DisplayName("Откат несуществующей транзакции - ошибка")
    void rollbackNonExistentTransactionThrowsException() throws Exception {
        mockMvc.perform(post("/api/transactions/{id}/rollback", 99999L)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Повтор откатанной транзакции - успешно")
    void redoRolledBackTransactionSuccess() throws Exception {
        // Создаем транзакцию
        Long transactionId = getLastTransactionId();
        BigDecimal afterRefill = getBullionAmount(bullionId1);

        // Откатываем
        mockMvc.perform(post("/api/transactions/{id}/rollback", transactionId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        BigDecimal afterRollback = getBullionAmount(bullionId1);
        assertThat(afterRollback).isLessThan(afterRefill);


    }

    @Test
    @DisplayName("Получение цепочки операций для транзакции")
    void getTransactionChainSuccess() throws Exception {
        Long transactionId = getLastTransactionId();

        // Откатываем
        mockMvc.perform(post("/api/transactions/{id}/rollback", transactionId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // Получаем цепочку
        mockMvc.perform(get("/api/transactions/{id}/chain", transactionId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ==================== ТЕСТЫ НА ОШИБКИ ====================

    @Test
    @DisplayName("Попытка доступа без токена - ошибка")
    void accessWithoutTokenThrowsException() throws Exception {
        mockMvc.perform(get("/api/transactions/history"))
                .andExpect(status().isForbidden());
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private void performRefill(long amount) throws Exception {
        Long batchId = System.currentTimeMillis();

        String request = """
            {
                "categoryId": %d,
                "vaultId": %d,
                "amount": %d,
                "batchId": %d
            }
            """.formatted(categoryId1, vaultId1, amount, batchId);

        mockMvc.perform(post("/api/bullions/refill")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
    }

    private void performWithdraw(long amount) throws Exception {
        Long batchId = System.currentTimeMillis();

        String request = """
            {
                "categoryId": %d,
                "vaultId": %d,
                "amount": %d,
                "batchId": %d
            }
            """.formatted(categoryId1, vaultId1, amount, batchId);

        mockMvc.perform(post("/api/bullions/withdraw")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
    }

    private Long getLastTransactionId() throws Exception {
        // Сначала делаем транзакцию
        performRefill(50000);

        // Получаем последнюю транзакцию из истории
        MvcResult historyResult = mockMvc.perform(get("/api/transactions/history")
                        .param("page", "0")
                        .param("size", "1")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn();

        String responseBody = historyResult.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);

        if (jsonNode.has("content") && jsonNode.get("content").size() > 0) {
            return jsonNode.get("content").get(0).get("id").asLong();
        }
        return null;
    }

    private BigDecimal getBullionAmount(Long bullionId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/bullions/{bullionId}", bullionId)
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);

        if (jsonNode.has("data") && jsonNode.get("data").has("amount")) {
            return new BigDecimal(jsonNode.get("data").get("amount").asText());
        }
        return BigDecimal.ZERO;
    }
}