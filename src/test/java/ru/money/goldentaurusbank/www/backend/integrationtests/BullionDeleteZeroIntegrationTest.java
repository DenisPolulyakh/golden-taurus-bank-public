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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Удаление слитка без переноса остатка: DELETE /api/bullions/{id}.
 * Пустой слиток на этом эндпоинте архивируется начисто, слиток с остатком
 * отсюда не удаляется вовсе — для него есть /transfer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Интеграционные тесты удаления пустого слитка")
class BullionDeleteZeroIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String accessToken;
    private Long vaultId;
    private Long otherVaultId;
    private Long bullionNameId;

    @BeforeEach
    void setUp() throws Exception {
        String registerRequest = """
                {
                    "email": "bulliondeletezero@example.com",
                    "password": "Test123%",
                    "fullName": "Bullion Delete Zero User"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));

        User user = userRepository.findByEmail("bulliondeletezero@example.com").orElseThrow();

        mockMvc.perform(get("/api/auth/verify")
                .param("token", user.getVerificationToken()));

        String loginRequest = """
                {
                    "email": "bulliondeletezero@example.com",
                    "password": "Test123%"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andReturn();

        accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("data").get("token").asText();

        bullionNameId = createBullionName("Финансовая подушка");
        vaultId = createVault("Сбербанк");
        otherVaultId = createVault("ВТБ");
    }

    @Test
    @DisplayName("Пустой слиток архивируется, операции в истории не появляется")
    void deleteEmptyBullionArchivesWithoutTransaction() throws Exception {
        Long bullionId = createBullion(vaultId, "0");

        deleteBullion(bullionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Map<String, Object> row = bullionRow(bullionId);
        assertThat(row.get("archived")).isEqualTo(true);
        // Хранилище остаётся при слитке: строка живёт ради истории операций
        assertThat(row.get("vault_id")).isEqualTo(vaultId);
        assertThat(transactionCount(bullionId)).isZero();
    }

    @Test
    @DisplayName("Удалённый пустой слиток пропадает из списка и из сгруппированного списка")
    void deletedEmptyBullionDisappearsFromLists() throws Exception {
        Long bullionId = createBullion(vaultId, "0");

        deleteBullion(bullionId).andExpect(status().isOk());

        mockMvc.perform(get("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/bullions/grouped")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bullionNameBullionList").isEmpty());
    }

    @Test
    @DisplayName("Слиток с остатком без переноса не удаляется, история не трогается")
    void deleteFundedBullionThrowsAndKeepsHistory() throws Exception {
        Long bullionId = createBullion(vaultId, "1000.00");
        int before = transactionCount(bullionId);

        deleteBullion(bullionId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4038))
                .andExpect(jsonPath("$.message")
                        .value("У слитка есть остаток: удалить можно только с переносом остатка"));

        Map<String, Object> row = bullionRow(bullionId);
        assertThat(row.get("archived")).isEqualTo(false);
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("1000.00");
        // Отказ не должен оставлять за собой ни снятия, ни чего-либо ещё
        assertThat(transactionCount(bullionId)).isEqualTo(before);
    }

    @Test
    @DisplayName("Слиток с остатком по-прежнему удаляется с переносом в другое хранилище")
    void deleteFundedBullionWithTransferStillWorks() throws Exception {
        Long bullionId = createBullion(vaultId, "1000.00");

        String request = """
                {
                    "toVaultId": %d,
                    "toLiquidityVault": false
                }
                """.formatted(otherVaultId);

        mockMvc.perform(delete("/api/bullions/{bullionId}/transfer", bullionId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(bullionRow(bullionId).get("archived")).isEqualTo(true);
        assertThat(amountInVault(otherVaultId)).isEqualByComparingTo("1000.00");
    }

    private ResultActions deleteBullion(Long bullionId) throws Exception {
        return mockMvc.perform(delete("/api/bullions/{bullionId}", bullionId)
                .header("Authorization", "Bearer " + accessToken));
    }

    private Long createVault(String name) throws Exception {
        String request = """
                {
                    "name": "%s"
                }
                """.formatted(name);

        MvcResult result = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andReturn();

        return dataId(result);
    }

    private Long createBullionName(String title) throws Exception {
        String request = """
                {
                    "title": "%s"
                }
                """.formatted(title);

        MvcResult result = mockMvc.perform(post("/api/bullion-names")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andReturn();

        return dataId(result);
    }

    private Long createBullion(Long vault, String amount) throws Exception {
        String request = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": %s
                }
                """.formatted(bullionNameId, vault, amount);

        MvcResult result = mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andReturn();

        return dataId(result);
    }

    private Long dataId(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("data").get("id").asLong();
    }

    private Map<String, Object> bullionRow(Long bullionId) {
        return jdbcTemplate.queryForMap(
                "SELECT amount, archived, vault_id FROM taurus.bullions WHERE id = ?", bullionId);
    }

    private int transactionCount(Long bullionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM taurus.transactions "
                        + "WHERE source_bullion_id = ? OR target_bullion_id = ?",
                Integer.class, bullionId, bullionId);
        return count == null ? 0 : count;
    }

    /** Сумма живых слитков хранилища — то, что видит пользователь в списке. */
    private BigDecimal amountInVault(Long vault) {
        List<BigDecimal> amounts = jdbcTemplate.queryForList(
                "SELECT amount FROM taurus.bullions WHERE vault_id = ? AND NOT archived",
                BigDecimal.class, vault);
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
