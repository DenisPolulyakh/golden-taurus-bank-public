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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Статистика доходов по типу — группировка по дню/месяцу/году, пустые точки
 * нулями, исключение неклассифицированных и откаченных пополнений.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Интеграционные тесты статистики доходов")
class IncomeStatisticsIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private String accessToken;
    private Long bullionNameId;
    private Long vaultId;
    private Long bullionId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "email": "incomestats@example.com",
                            "password": "Test123%",
                            "fullName": "Income Stats User"
                        }
                        """));

        User user = userRepository.findByEmail("incomestats@example.com").orElseThrow();
        mockMvc.perform(get("/api/auth/verify").param("token", user.getVerificationToken()));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "incomestats@example.com",
                                    "password": "Test123%"
                                }
                                """))
                .andReturn();
        accessToken = json(loginResult).get("data").get("token").asText();

        bullionNameId = createBullionName("Подушка");
        vaultId = createVault("Сбербанк");
        // Существенный стартовый остаток — если бы статистика фильтровала не по
        // income_type, а по факту пополнения, он бы протёк в статистику доходов.
        bullionId = createBullion(bullionNameId, vaultId, "100000");
    }

    @Test
    @DisplayName("Месяц: 12 точек, суммы по типам верные, стартовый остаток и неклассифицированное не в счёт")
    void monthGranularityGroupsByMonthWithCorrectSums() throws Exception {
        performRefill(10000, "SALARY", "2026-01-10T12:00:00");
        performRefill(500, "CASHBACK", "2026-01-15T12:00:00");
        performRefill(20000, "SALARY", "2026-03-10T12:00:00");
        performRefill(7000, null, "2026-01-20T12:00:00"); // без классификации — не в счёт

        MvcResult result = mockMvc.perform(get("/api/transactions/income-statistics")
                        .param("granularity", "MONTH")
                        .param("year", "2026")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(12))
                .andReturn();

        JsonNode dto = json(result);
        JsonNode january = dto.get("points").get(0);
        assertThat(new BigDecimal(january.get("total").asText())).isEqualByComparingTo("10500");
        assertThat(new BigDecimal(january.get("byType").get("SALARY").asText())).isEqualByComparingTo("10000");
        assertThat(new BigDecimal(january.get("byType").get("CASHBACK").asText())).isEqualByComparingTo("500");

        JsonNode march = dto.get("points").get(2);
        assertThat(new BigDecimal(march.get("total").asText())).isEqualByComparingTo("20000");

        JsonNode february = dto.get("points").get(1);
        assertThat(new BigDecimal(february.get("total").asText())).isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(new BigDecimal(dto.get("total").asText())).isEqualByComparingTo("30500");
    }

    @Test
    @DisplayName("Откаченное пополнение не входит в статистику")
    void reversedRefillNotCounted() throws Exception {
        performRefill(15000, "SALARY", "2026-05-10T12:00:00");
        Long depositId = lastHistoryEntryId();

        mockMvc.perform(post("/api/transactions/{id}/rollback", depositId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/transactions/income-statistics")
                        .param("granularity", "MONTH")
                        .param("year", "2026")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(new BigDecimal(json(result).get("total").asText())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Чужие операции не попадают в статистику")
    void otherUsersOperationsNotCounted() throws Exception {
        performRefill(9000, "SALARY", "2026-06-10T12:00:00");

        // Второй пользователь со своим классифицированным доходом
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "email": "incomestats-other@example.com",
                            "password": "Test123%",
                            "fullName": "Other User"
                        }
                        """));
        User other = userRepository.findByEmail("incomestats-other@example.com").orElseThrow();
        mockMvc.perform(get("/api/auth/verify").param("token", other.getVerificationToken()));
        MvcResult otherLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "incomestats-other@example.com",
                                    "password": "Test123%"
                                }
                                """))
                .andReturn();
        String otherToken = json(otherLogin).get("data").get("token").asText();

        MvcResult nameResult = mockMvc.perform(post("/api/bullion-names")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Чужая подушка\"}"))
                .andReturn();
        Long otherBullionNameId = json(nameResult).get("data").get("id").asLong();
        MvcResult vaultResult = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Чужой банк\"}"))
                .andReturn();
        Long otherVaultId = json(vaultResult).get("data").get("id").asLong();
        // Слиток сначала заводится пустым: сумма при СОЗДАНИИ — это стартовый
        // остаток, у него типа дохода не бывает (см. IncomeTypeRefillIntegrationTest).
        // Чтобы тип действительно сохранился, доход заводится отдельным пополнением.
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "bullionNameId": %d,
                                    "vaultId": %d,
                                    "amount": 0,
                                    "dateOperation": "2026-06-11T12:00:00"
                                }
                                """.formatted(otherBullionNameId, otherVaultId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bullions/refill")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "bullionNameId": %d,
                                    "vaultId": %d,
                                    "amount": 999999,
                                    "incomeType": "WINDFALL",
                                    "dateOperation": "2026-06-11T12:00:00"
                                }
                                """.formatted(otherBullionNameId, otherVaultId)))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/transactions/income-statistics")
                        .param("granularity", "MONTH")
                        .param("year", "2026")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(new BigDecimal(json(result).get("total").asText())).isEqualByComparingTo("9000");
    }

    @Test
    @DisplayName("День: границы месяца учитываются, доход в последний день попадает в свою точку")
    void dayGranularityRespectsMonthBoundaries() throws Exception {
        performRefill(1000, "ADVANCE", "2026-02-01T00:00:01");
        performRefill(2000, "ADVANCE", "2026-02-28T23:59:00");

        MvcResult result = mockMvc.perform(get("/api/transactions/income-statistics")
                        .param("granularity", "DAY")
                        .param("year", "2026")
                        .param("month", "2")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(28))
                .andReturn();

        JsonNode points = json(result).get("points");
        assertThat(new BigDecimal(points.get(0).get("total").asText())).isEqualByComparingTo("1000");
        assertThat(new BigDecimal(points.get(27).get("total").asText())).isEqualByComparingTo("2000");
        for (int i = 1; i < 27; i++) {
            assertThat(new BigDecimal(points.get(i).get("total").asText())).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Test
    @DisplayName("Год: годы без доходов между годами с доходами дают ноль")
    void yearGranularityFillsGapYears() throws Exception {
        performRefill(5000, "DIVIDENDS", "2023-04-01T12:00:00");
        performRefill(6000, "DIVIDENDS", "2025-04-01T12:00:00");

        MvcResult result = mockMvc.perform(get("/api/transactions/income-statistics")
                        .param("granularity", "YEAR")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode points = json(result).get("points");
        java.util.Map<String, BigDecimal> byPeriod = new java.util.HashMap<>();
        points.forEach(p -> byPeriod.put(p.get("period").asText(), new BigDecimal(p.get("total").asText())));

        assertThat(byPeriod.get("2023")).isEqualByComparingTo("5000");
        assertThat(byPeriod.get("2024")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(byPeriod.get("2025")).isEqualByComparingTo("6000");
    }

    @Test
    @DisplayName("Неверный granularity отклоняется с 400")
    void invalidGranularityReturns400() throws Exception {
        mockMvc.perform(get("/api/transactions/income-statistics")
                        .param("granularity", "WEEK")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Справочник типов дохода отдаёт все 9 значений с названиями")
    void incomeTypesEndpointReturnsAllValues() throws Exception {
        mockMvc.perform(get("/api/transactions/income-types")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[0].code").value("ADVANCE"))
                .andExpect(jsonPath("$[0].displayName").value("Аванс"));
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

    private Long createBullion(Long bullionNameIdParam, Long vaultIdParam, String amount) throws Exception {
        String request = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": %s,
                    "dateOperation": "2026-01-01T00:00:00"
                }
                """.formatted(bullionNameIdParam, vaultIdParam, amount);

        MvcResult result = mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("data").get("id").asLong();
    }

    private void performRefill(long amount, String incomeType, String dateOperation) throws Exception {
        String incomeTypeJson = incomeType == null ? "" : ",\n\"incomeType\": \"%s\"".formatted(incomeType);
        String request = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": %d,
                    "dateOperation": "%s"%s
                }
                """.formatted(bullionNameId, vaultId, amount, dateOperation, incomeTypeJson);

        mockMvc.perform(post("/api/bullions/refill")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
    }

    private Long lastHistoryEntryId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/transactions/history")
                        .param("page", "0")
                        .param("size", "1")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn();
        return json(result).get("content").get(0).get("id").asLong();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
