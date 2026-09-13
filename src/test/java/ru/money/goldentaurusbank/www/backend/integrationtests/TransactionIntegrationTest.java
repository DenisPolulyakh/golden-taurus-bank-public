package ru.money.goldentaurusbank.www.backend.integrationtests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.repository.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Интеграционные тесты транзакций")
class TransactionIntegrationTest extends IntegrationTestBase {

    private static final String OPERATION_DATE = "2026-07-20T12:00:00";
    private static final BigDecimal OPENING_1 = new BigDecimal("100000");
    private static final BigDecimal OPENING_2 = new BigDecimal("50000");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BullionNameRepository bullionNameRepository;

    @Autowired
    private VaultRepository vaultRepository;

    @Autowired
    private BullionRepository bullionRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private String accessToken;
    private Long bullionNameId1;
    private Long bullionNameId2;
    private Long vaultId1;
    private Long vaultId2;
    private Long bullionId1;
    private Long bullionId2;

    @BeforeEach
    void setUp() throws Exception {
        transactionRepository.deleteAll();
        bullionRepository.deleteAll();
        vaultRepository.deleteAll();
        bullionNameRepository.deleteAll();
        userRepository.deleteAll();

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

        User user = userRepository.findByEmail("transactionuser@example.com").orElseThrow();

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

        accessToken = json(loginResult).get("data").get("token").asText();

        bullionNameId1 = createBullionName("Финансовая подушка");
        bullionNameId2 = createBullionName("Накопления");
        vaultId1 = createVault("Сбербанк");
        vaultId2 = createVault("Тинькофф");

        // Создание слитка с суммой пишется стартовым остатком (opening_balance),
        // а не пополнением — см. отдельный тест ниже.
        bullionId1 = createBullion(bullionNameId1, vaultId1, OPENING_1);
        bullionId2 = createBullion(bullionNameId2, vaultId2, OPENING_2);
    }

    // ==================== ДВИЖЕНИЕ НАКОПЛЕНИЙ ====================

    @Test
    @DisplayName("Перевод между слитками не меняет накопления и создаёт ровно одну запись")
    void transferKeepsSavingsAndWritesSingleRow() throws Exception {
        long transactionsBefore = countTransactions();
        BigDecimal savingsBefore = totalAmount();

        performTransfer(bullionId1, bullionId2, 30000);

        assertThat(countTransactions()).isEqualTo(transactionsBefore + 1);
        assertThat(totalAmount()).isEqualByComparingTo(savingsBefore);
        assertThat(bullionAmount(bullionId1)).isEqualByComparingTo(OPENING_1.subtract(new BigDecimal("30000")));
        assertThat(bullionAmount(bullionId2)).isEqualByComparingTo(OPENING_2.add(new BigDecimal("30000")));

        MvcResult result = mockMvc.perform(get("/api/transactions/history")
                        .param("kind", "TRANSFER")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].kind").value("TRANSFER"))
                .andReturn();

        JsonNode transfer = json(result).get("content").get(0);
        assertThat(new BigDecimal(transfer.get("signedAmount").asText())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Пополнение и снятие двигают накопления на свою величину")
    void depositAndWithdrawalMoveSavings() throws Exception {
        BigDecimal savingsBefore = totalAmount();

        performRefill(40000);
        assertThat(totalAmount()).isEqualByComparingTo(savingsBefore.add(new BigDecimal("40000")));

        performWithdraw(15000);
        assertThat(totalAmount()).isEqualByComparingTo(savingsBefore.add(new BigDecimal("25000")));
    }

    @Test
    @DisplayName("Снятие больше остатка отклоняется и не пишет запись")
    void withdrawalOverBalanceIsRejected() throws Exception {
        long transactionsBefore = countTransactions();

        performWithdrawExpecting(OPENING_1.longValue() + 1, status().isBadRequest());

        assertThat(countTransactions()).isEqualTo(transactionsBefore);
        assertThat(bullionAmount(bullionId1)).isEqualByComparingTo(OPENING_1);
    }

    // ==================== СТАТИСТИКА ====================

    @Test
    @DisplayName("Начальный остаток не попадает в «Доход за месяц»")
    void openingBalanceIsNotIncome() throws Exception {
        // В setUp заведены только стартовые остатки, обычных операций ещё не было.
        MvcResult result = mockMvc.perform(get("/api/transactions/dashboard/statistics")
                        .param("year", "2026")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode statistics = json(result);
        assertThat(new BigDecimal(statistics.get("totalIncome").asText())).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(new BigDecimal(statistics.get("totalExpense").asText())).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(new BigDecimal(statistics.get("totalAmount").asText()))
                .isEqualByComparingTo(OPENING_1.add(OPENING_2));
    }

    @Test
    @DisplayName("Правый край графика равен сумме слитков, месяц без операций держит накопления")
    void chartRightEdgeMatchesTotalAndEmptyMonthsHoldSavings() throws Exception {
        performRefill(40000);
        performWithdraw(10000);

        MvcResult result = mockMvc.perform(get("/api/transactions/dashboard/statistics")
                        .param("year", "2026")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyData.length()").value(12))
                .andReturn();

        JsonNode monthlyData = json(result).get("monthlyData");
        BigDecimal expected = totalAmount();

        BigDecimal lastMonthSavings = new BigDecimal(monthlyData.get(11).get("savings").asText());
        assertThat(lastMonthSavings).isEqualByComparingTo(expected);

        // Операции датированы июлем; август–декабрь пустые и обязаны сохранить накопления июля.
        BigDecimal july = new BigDecimal(monthlyData.get(6).get("savings").asText());
        for (int month = 7; month < 12; month++) {
            assertThat(new BigDecimal(monthlyData.get(month).get("savings").asText()))
                    .isEqualByComparingTo(july);
        }
    }

    @Test
    @DisplayName("Доступные года берутся по дате операции")
    void availableYearsComeFromOperationDate() throws Exception {
        mockMvc.perform(get("/api/transactions/available-years")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0]").value(2026));
    }

    // ==================== ИСТОРИЯ ====================

    @Test
    @DisplayName("История отдаёт вид операции и признак возможности отката")
    void historyExposesKindAndRollbackFlag() throws Exception {
        performRefill(100000);
        performWithdraw(30000);

        MvcResult result = mockMvc.perform(get("/api/transactions/history")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].kind").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.content[0].canRollback").value(true))
                .andExpect(jsonPath("$.content[0].reversedById").doesNotExist())
                .andReturn();

        JsonNode withdrawal = json(result).get("content").get(0);
        assertThat(new BigDecimal(withdrawal.get("signedAmount").asText()))
                .isEqualByComparingTo(new BigDecimal("-30000"));
    }

    @Test
    @DisplayName("Фильтр истории по виду операции")
    void historyFilterByKind() throws Exception {
        performRefill(100000);
        performRefill(50000);
        performWithdraw(30000);

        mockMvc.perform(get("/api/transactions/history")
                        .param("kind", "DEPOSIT")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].kind").value("DEPOSIT"));

        mockMvc.perform(get("/api/transactions/history")
                        .param("kind", "OPENING_BALANCE")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("Пагинация истории")
    void historyPagination() throws Exception {
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

    // ==================== ОТКАТ ====================

    @Test
    @DisplayName("Откат перевода возвращает оба слитка и создаёт обратную запись")
    void rollbackOfTransferRestoresBothBullions() throws Exception {
        performTransfer(bullionId1, bullionId2, 30000);
        Long transferId = lastTransactionId();
        long transactionsBefore = countTransactions();

        rollback(transferId).andExpect(status().isOk());

        assertThat(countTransactions()).isEqualTo(transactionsBefore + 1);
        assertThat(bullionAmount(bullionId1)).isEqualByComparingTo(OPENING_1);
        assertThat(bullionAmount(bullionId2)).isEqualByComparingTo(OPENING_2);

        mockMvc.perform(get("/api/transactions/{id}/chain", transferId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].reversalOfId").value(transferId));
    }

    @Test
    @DisplayName("Откат не затирает более позднюю операцию")
    void rollbackDoesNotWipeLaterOperations() throws Exception {
        performRefill(40000);
        Long refillId = lastTransactionId();

        performRefill(5000);

        rollback(refillId).andExpect(status().isOk());

        // 100000 стартовых + 40000 + 5000 − 40000 отката: поздние 5000 должны уцелеть.
        assertThat(bullionAmount(bullionId1)).isEqualByComparingTo(OPENING_1.add(new BigDecimal("5000")));
    }

    @Test
    @DisplayName("Повторный откат той же транзакции отклоняется")
    void secondRollbackOfSameTransactionIsRejected() throws Exception {
        performRefill(40000);
        Long refillId = lastTransactionId();

        rollback(refillId).andExpect(status().isOk());
        rollback(refillId).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Откаченная операция помечается как неоткатываемая")
    void reversedTransactionIsMarkedInHistory() throws Exception {
        performRefill(40000);
        Long refillId = lastTransactionId();

        rollback(refillId).andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/transactions/{id}/chain", refillId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode original = json(result).get(0);
        assertThat(original.get("canRollback").asBoolean()).isFalse();
        assertThat(original.get("reversedById").asLong()).isPositive();
    }

    @Test
    @DisplayName("Откат последней транзакции")
    void rollbackLastTransaction() throws Exception {
        performRefill(50000);
        BigDecimal beforeRollback = bullionAmount(bullionId1);

        mockMvc.perform(post("/api/transactions/rollback-last")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        assertThat(bullionAmount(bullionId1))
                .isEqualByComparingTo(beforeRollback.subtract(new BigDecimal("50000")));
    }

    @Test
    @DisplayName("Откат несуществующей транзакции — ошибка")
    void rollbackOfMissingTransactionFails() throws Exception {
        rollback(99999L).andExpect(status().isNotFound());
    }

    // ==================== ДОСТУП ====================

    @Test
    @DisplayName("Обращение без токена — ошибка")
    void accessWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/transactions/history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(3002));
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private Long createBullionName(String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/bullion-names")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"%s\"}".formatted(title)))
                .andReturn();
        return json(result).get("data").get("id").asLong();
    }

    private Long createVault(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\"}".formatted(name)))
                .andReturn();
        return json(result).get("data").get("id").asLong();
    }

    private Long createBullion(Long bullionNameId, Long vaultId, BigDecimal amount) throws Exception {
        String request = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": %s,
                    "dateOperation": "2026-07-20T11:59:00"
                }
                """.formatted(bullionNameId, vaultId, amount.toPlainString());

        MvcResult result = mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("data").get("id").asLong();
    }

    private void performRefill(long amount) throws Exception {
        String request = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": %d,
                    "dateOperation": "%s"
                }
                """.formatted(bullionNameId1, vaultId1, amount, OPERATION_DATE);

        mockMvc.perform(post("/api/bullions/refill")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
    }

    private void performWithdraw(long amount) throws Exception {
        performWithdrawExpecting(amount, status().isOk());
    }

    private void performWithdrawExpecting(long amount, ResultMatcher matcher) throws Exception {
        String request = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": %d,
                    "dateOperation": "%s"
                }
                """.formatted(bullionNameId1, vaultId1, amount, OPERATION_DATE);

        mockMvc.perform(post("/api/bullions/withdraw")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(matcher);
    }

    private void performTransfer(Long fromBullionId, Long toBullionId, long amount) throws Exception {
        String request = """
                {
                    "fromBullionId": %d,
                    "toBullionId": %d,
                    "amount": %d,
                    "dateOperation": "%s"
                }
                """.formatted(fromBullionId, toBullionId, amount, OPERATION_DATE);

        mockMvc.perform(post("/api/bullions/transfer")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
    }

    private ResultActions rollback(Long transactionId) throws Exception {
        return mockMvc.perform(post("/api/transactions/{id}/rollback", transactionId)
                .header("Authorization", "Bearer " + accessToken));
    }

    private Long lastTransactionId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/transactions/history")
                        .param("page", "0")
                        .param("size", "1")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn();
        return json(result).get("content").get(0).get("id").asLong();
    }

    private long countTransactions() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/transactions/history")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn();
        return json(result).get("totalElements").asLong();
    }

    private BigDecimal totalAmount() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/transactions/dashboard/statistics")
                        .param("year", "2026")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn();
        return new BigDecimal(json(result).get("totalAmount").asText());
    }

    private BigDecimal bullionAmount(Long bullionId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/bullions/{bullionId}", bullionId)
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn();
        return new BigDecimal(json(result).get("data").get("amount").asText());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
