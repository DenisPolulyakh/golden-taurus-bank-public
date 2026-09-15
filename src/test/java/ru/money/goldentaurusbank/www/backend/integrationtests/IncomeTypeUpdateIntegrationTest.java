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
import org.springframework.transaction.annotation.Transactional;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Правка типа дохода у уже проведённой операции: доступна только у своего
 * пополнения, которое не откачено.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Интеграционные тесты правки типа дохода")
class IncomeTypeUpdateIntegrationTest extends IntegrationTestBase {

    private static final int ACCESS_DENIED = 3001;
    private static final int INCOME_TYPE_UPDATE_NOT_ALLOWED = 4044;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private String accessToken;
    private Long bullionNameId1;
    private Long vaultId1;
    private Long bullionId1;
    private Long bullionId2;
    private Long unclassifiedDepositId;
    private Long classifiedDepositId;
    private Long withdrawalId;
    private Long transferId;
    private Long openingBalanceId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "email": "incometypeupdate@example.com",
                            "password": "Test123%",
                            "fullName": "Income Type Update User"
                        }
                        """));

        User user = userRepository.findByEmail("incometypeupdate@example.com").orElseThrow();
        mockMvc.perform(get("/api/auth/verify").param("token", user.getVerificationToken()));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "incometypeupdate@example.com",
                                    "password": "Test123%"
                                }
                                """))
                .andReturn();
        accessToken = json(loginResult).get("data").get("token").asText();

        bullionNameId1 = createBullionName("Подушка");
        Long bullionNameId2 = createBullionName("Накопления");
        vaultId1 = createVault("Сбербанк");
        Long vaultId2 = createVault("Тинькофф");

        // Стартовый остаток слитка — тоже операция, для которой тип запрещён.
        bullionId1 = createBullion(bullionNameId1, vaultId1, "50000");
        bullionId2 = createBullion(bullionNameId2, vaultId2, "10000");
        openingBalanceId = lastHistoryEntryId();

        unclassifiedDepositId = performRefill(bullionNameId1, vaultId1, 10000, null);
        classifiedDepositId = performRefill(bullionNameId1, vaultId1, 20000, "ADVANCE");
        withdrawalId = performWithdraw(5000);
        transferId = performTransfer(bullionId1, bullionId2, 1000);
    }

    @Test
    @DisplayName("Тип дохода можно проставить у пополнения без классификации")
    void setsIncomeTypeOnUnclassifiedDeposit() throws Exception {
        MvcResult result = updateIncomeType(unclassifiedDepositId, "CASHBACK", accessToken)
                .andExpect(status().isOk())
                .andReturn();

        assertThat(json(result).get("incomeType").asText()).isEqualTo("CASHBACK");
        assertThat(fetchHistoryEntry(unclassifiedDepositId).get("incomeType").asText()).isEqualTo("CASHBACK");
    }

    @Test
    @DisplayName("Тип дохода можно сбросить обратно в «без классификации»")
    void resetsIncomeTypeToNull() throws Exception {
        MvcResult result = updateIncomeType(classifiedDepositId, null, accessToken)
                .andExpect(status().isOk())
                .andReturn();

        assertThat(json(result).get("incomeType").isNull()).isTrue();
        assertThat(fetchHistoryEntry(classifiedDepositId).get("incomeType").isNull()).isTrue();
    }

    @Test
    @DisplayName("Чужая операция недоступна для правки")
    void foreignTransactionIsForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "email": "incometypeupdate-other@example.com",
                            "password": "Test123%",
                            "fullName": "Other User"
                        }
                        """));
        User other = userRepository.findByEmail("incometypeupdate-other@example.com").orElseThrow();
        mockMvc.perform(get("/api/auth/verify").param("token", other.getVerificationToken()));
        MvcResult otherLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "incometypeupdate-other@example.com",
                                    "password": "Test123%"
                                }
                                """))
                .andReturn();
        String otherToken = json(otherLogin).get("data").get("token").asText();

        updateIncomeType(unclassifiedDepositId, "SALARY", otherToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ACCESS_DENIED));
    }

    @Test
    @DisplayName("Тип дохода нельзя проставить на снятии")
    void cannotSetIncomeTypeOnWithdrawal() throws Exception {
        updateIncomeType(withdrawalId, "SALARY", accessToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INCOME_TYPE_UPDATE_NOT_ALLOWED));
    }

    @Test
    @DisplayName("Тип дохода нельзя проставить на переводе")
    void cannotSetIncomeTypeOnTransfer() throws Exception {
        updateIncomeType(transferId, "SALARY", accessToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INCOME_TYPE_UPDATE_NOT_ALLOWED));
    }

    @Test
    @DisplayName("Тип дохода нельзя проставить на стартовом остатке")
    void cannotSetIncomeTypeOnOpeningBalance() throws Exception {
        updateIncomeType(openingBalanceId, "SALARY", accessToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INCOME_TYPE_UPDATE_NOT_ALLOWED));
    }

    @Test
    @DisplayName("Тип дохода нельзя менять у откаченного пополнения")
    void cannotUpdateReversedDeposit() throws Exception {
        mockMvc.perform(post("/api/transactions/{id}/rollback", classifiedDepositId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        updateIncomeType(classifiedDepositId, "SALARY", accessToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INCOME_TYPE_UPDATE_NOT_ALLOWED));
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private org.springframework.test.web.servlet.ResultActions updateIncomeType(Long transactionId, String incomeType,
                                                                                  String token) throws Exception {
        String body = incomeType == null ? "{}" : "{\"incomeType\": \"%s\"}".formatted(incomeType);
        return mockMvc.perform(patch("/api/transactions/{id}/income-type", transactionId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

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

    private Long performRefill(Long bullionNameId, Long vaultId, long amount, String incomeType) throws Exception {
        String incomeTypeJson = incomeType == null ? "" : ",\n\"incomeType\": \"%s\"".formatted(incomeType);
        String request = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": %d,
                    "dateOperation": "2026-07-20T12:00:00"%s
                }
                """.formatted(bullionNameId, vaultId, amount, incomeTypeJson);

        mockMvc.perform(post("/api/bullions/refill")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
        return lastHistoryEntryId();
    }

    private Long performWithdraw(long amount) throws Exception {
        String request = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": %d,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId1, vaultId1, amount);

        mockMvc.perform(post("/api/bullions/withdraw")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
        return lastHistoryEntryId();
    }

    private Long performTransfer(Long fromBullionId, Long toBullionId, long amount) throws Exception {
        String request = """
                {
                    "fromBullionId": %d,
                    "toBullionId": %d,
                    "amount": %d,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(fromBullionId, toBullionId, amount);

        mockMvc.perform(post("/api/bullions/transfer")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
        return lastHistoryEntryId();
    }

    private Long lastHistoryEntryId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/transactions/history")
                        .param("page", "0")
                        .param("size", "1")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn();
        return json(result).get("content").get(0).get("id").asLong();
    }

    private JsonNode fetchHistoryEntry(Long transactionId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/transactions/history")
                        .param("size", "50")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn();
        JsonNode content = json(result).get("content");
        for (JsonNode node : content) {
            if (node.get("id").asLong() == transactionId) {
                return node;
            }
        }
        throw new IllegalStateException("Транзакция не найдена в истории: " + transactionId);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
