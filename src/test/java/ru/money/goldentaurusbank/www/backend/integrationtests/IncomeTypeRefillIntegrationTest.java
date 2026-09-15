package ru.money.goldentaurusbank.www.backend.integrationtests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.money.goldentaurusbank.www.backend.model.domain.Transaction;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.repository.TransactionRepository;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Тип дохода при пополнении слитка: сохраняется только у пополнения, только
 * если выбран явно, и не переживает откат.
 *
 * <p>Класс <b>без</b> {@code @Transactional}: один из тестов пишет запись
 * напрямую через репозиторий и ждёт нарушения ограничения БД —
 * в общей с тестом транзакции она пометила бы её rollback-only, не давая
 * проверить остальные утверждения. Чистоту базы обеспечивает {@code TRUNCATE}
 * из {@link IntegrationTestBase}, как у {@code TransferToNewBullionIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Интеграционные тесты типа дохода при пополнении")
class IncomeTypeRefillIntegrationTest extends IntegrationTestBase {

    private static final String OPERATION_DATE = "2026-07-20T12:00:00";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private String accessToken;
    private Long bullionNameId;
    private Long vaultId;
    private Long bullionId;
    private Long bullionId2;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "email": "incometyperefill@example.com",
                            "password": "Test123%",
                            "fullName": "Income Type Refill User"
                        }
                        """));

        User user = userRepository.findByEmail("incometyperefill@example.com").orElseThrow();
        mockMvc.perform(get("/api/auth/verify").param("token", user.getVerificationToken()));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "incometyperefill@example.com",
                                    "password": "Test123%"
                                }
                                """))
                .andReturn();
        accessToken = json(loginResult).get("data").get("token").asText();

        bullionNameId = createBullionName("Подушка");
        vaultId = createVault("Сбербанк");
        bullionId = createBullion(bullionNameId, vaultId, "10000");

        Long bullionNameId2 = createBullionName("Накопления");
        Long vaultId2 = createVault("Тинькофф");
        bullionId2 = createBullion(bullionNameId2, vaultId2, "5000");
    }

    @Test
    @DisplayName("Пополнение с типом дохода сохраняет тип")
    void refillWithIncomeTypeSavesIt() throws Exception {
        performRefill(bullionId, vaultId, 50000, "SALARY").andExpect(status().isOk());

        JsonNode entry = lastHistoryEntry();
        assertThat(entry.get("kind").asText()).isEqualTo("DEPOSIT");
        assertThat(entry.get("incomeType").asText()).isEqualTo("SALARY");
    }

    @Test
    @DisplayName("Пополнение без типа дохода остаётся без классификации")
    void refillWithoutIncomeTypeStaysNull() throws Exception {
        performRefill(bullionId, vaultId, 50000, null).andExpect(status().isOk());

        JsonNode entry = lastHistoryEntry();
        assertThat(entry.get("incomeType").isNull()).isTrue();
    }

    @Test
    @DisplayName("Пополнение существующего слитка через создание сохраняет тип дохода")
    void createExistingBullionWithIncomeTypeSavesIt() throws Exception {
        String request = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 20000,
                    "incomeType": "DIVIDENDS",
                    "dateOperation": "%s"
                }
                """.formatted(bullionNameId, vaultId, OPERATION_DATE);

        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());

        JsonNode entry = lastHistoryEntry();
        assertThat(entry.get("kind").asText()).isEqualTo("DEPOSIT");
        assertThat(entry.get("incomeType").asText()).isEqualTo("DIVIDENDS");
    }

    @Test
    @DisplayName("Откат классифицированного пополнения не копирует тип дохода")
    void rollbackOfClassifiedRefillHasNoIncomeType() throws Exception {
        performRefill(bullionId, vaultId, 50000, "CASHBACK").andExpect(status().isOk());
        Long depositId = lastHistoryEntry().get("id").asLong();

        mockMvc.perform(post("/api/transactions/{id}/rollback", depositId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        JsonNode reversal = lastHistoryEntry();
        assertThat(reversal.get("kind").asText()).isEqualTo("WITHDRAWAL");
        assertThat(reversal.get("reversalOfId").asLong()).isEqualTo(depositId);
        assertThat(reversal.get("incomeType").isNull()).isTrue();
    }

    @Test
    @DisplayName("Неизвестный тип дохода в запросе отклоняется")
    void unknownIncomeTypeValueIsRejected() throws Exception {
        String request = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 1000,
                    "incomeType": "NOT_A_REAL_TYPE",
                    "dateOperation": "%s"
                }
                """.formatted(bullionNameId, vaultId, OPERATION_DATE);

        mockMvc.perform(post("/api/bullions/refill")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("БД отклоняет тип дохода на переводе между слитками")
    void databaseRejectsIncomeTypeOnTransfer() {
        Transaction transfer = Transaction.builder()
                .userId(userRepository.findByEmail("incometyperefill@example.com").orElseThrow().getId())
                .dateOperation(java.time.LocalDateTime.now())
                .amount(new BigDecimal("100.00"))
                .sourceBullionId(bullionId)
                .targetBullionId(bullionId2)
                .incomeType(ru.money.goldentaurusbank.www.backend.model.dto.enums.IncomeType.OTHER)
                .build();

        assertThatThrownBy(() -> transactionRepository.saveAndFlush(transfer))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("БД отклоняет тип дохода на снятии со слитка")
    void databaseRejectsIncomeTypeOnWithdrawal() {
        Transaction withdrawal = Transaction.builder()
                .userId(userRepository.findByEmail("incometyperefill@example.com").orElseThrow().getId())
                .dateOperation(java.time.LocalDateTime.now())
                .amount(new BigDecimal("100.00"))
                .sourceBullionId(bullionId)
                .incomeType(ru.money.goldentaurusbank.www.backend.model.dto.enums.IncomeType.OTHER)
                .build();

        assertThatThrownBy(() -> transactionRepository.saveAndFlush(withdrawal))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("БД отклоняет тип дохода на стартовом остатке")
    void databaseRejectsIncomeTypeOnOpeningBalance() {
        Transaction opening = Transaction.builder()
                .userId(userRepository.findByEmail("incometyperefill@example.com").orElseThrow().getId())
                .dateOperation(java.time.LocalDateTime.now())
                .amount(new BigDecimal("100.00"))
                .targetBullionId(bullionId)
                .openingBalance(true)
                .incomeType(ru.money.goldentaurusbank.www.backend.model.dto.enums.IncomeType.OTHER)
                .build();

        assertThatThrownBy(() -> transactionRepository.saveAndFlush(opening))
                .isInstanceOf(DataIntegrityViolationException.class);
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

    private Long createBullion(Long bullionNameId, Long vaultId, String amount) throws Exception {
        String request = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": %s,
                    "dateOperation": "2026-07-20T11:59:00"
                }
                """.formatted(bullionNameId, vaultId, amount);

        MvcResult result = mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("data").get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions performRefill(Long bullionNameIdParam, Long vaultIdParam,
                                                                              long amount, String incomeType) throws Exception {
        String incomeTypeJson = incomeType == null ? "" : ",\n\"incomeType\": \"%s\"".formatted(incomeType);
        String request = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": %d,
                    "dateOperation": "%s"%s
                }
                """.formatted(bullionNameIdParam, vaultIdParam, amount, OPERATION_DATE, incomeTypeJson);

        return mockMvc.perform(post("/api/bullions/refill")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));
    }

    private JsonNode lastHistoryEntry() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/transactions/history")
                        .param("page", "0")
                        .param("size", "1")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn();
        return json(result).get("content").get(0);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
