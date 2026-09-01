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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Уборка пустых слитков хранилища: DELETE /api/vaults/{id}/empty-bullions.
 * Слитки с нулём уходят в архив пачкой, всё остальное хранилище не трогается.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Интеграционные тесты уборки пустых слитков хранилища")
class VaultEmptyBullionsIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String accessToken;
    private String otherAccessToken;
    private Long vaultId;
    private Long bullionNameId1;
    private Long bullionNameId2;
    private Long bullionNameId3;

    @BeforeEach
    void setUp() throws Exception {
        accessToken = registerAndLogin("emptybullions@example.com");
        otherAccessToken = registerAndLogin("emptybullions-other@example.com");

        bullionNameId1 = createBullionName("Финансовая подушка");
        bullionNameId2 = createBullionName("Накопления");
        bullionNameId3 = createBullionName("Отпуск");
        vaultId = createVault("Сбербанк", null);
    }

    @Test
    @DisplayName("Уборка архивирует только нулевые слитки, слиток с остатком цел")
    void deleteEmptyBullionsArchivesOnlyEmptyOnes() throws Exception {
        Long emptyFirst = createBullion(vaultId, bullionNameId1, "0");
        Long emptySecond = createBullion(vaultId, bullionNameId2, "0");
        Long funded = createBullion(vaultId, bullionNameId3, "1500.00");

        deleteEmptyBullions(accessToken, vaultId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(2));

        assertThat(bullionRow(emptyFirst).get("archived")).isEqualTo(true);
        assertThat(bullionRow(emptySecond).get("archived")).isEqualTo(true);

        Map<String, Object> fundedRow = bullionRow(funded);
        assertThat(fundedRow.get("archived")).isEqualTo(false);
        assertThat((BigDecimal) fundedRow.get("amount")).isEqualByComparingTo("1500.00");

        // Уборка деньги не двигает: в истории осталась только заводка слитка с остатком
        assertThat(transactionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Повторная уборка возвращает ноль и ничего не ломает")
    void repeatedCleanupReturnsZero() throws Exception {
        Long emptyBullionId = createBullion(vaultId, bullionNameId1, "0");

        deleteEmptyBullions(accessToken, vaultId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));

        deleteEmptyBullions(accessToken, vaultId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0));

        assertThat(bullionRow(emptyBullionId).get("archived")).isEqualTo(true);
    }

    @Test
    @DisplayName("Счётчик пустых слитков в списке хранилищ падает до нуля после уборки")
    void emptyBullionsCountDropsAfterCleanup() throws Exception {
        createBullion(vaultId, bullionNameId1, "0");
        createBullion(vaultId, bullionNameId2, "0");
        createBullion(vaultId, bullionNameId3, "1500.00");

        assertThat(emptyBullionsCountFromList()).isEqualTo(2);

        deleteEmptyBullions(accessToken, vaultId).andExpect(status().isOk());

        // Архивные в счёт не идут: у хранилища остался один слиток, и тот с остатком
        assertThat(emptyBullionsCountFromList()).isZero();
    }

    @Test
    @DisplayName("Уборка в чужом хранилище - хранилище не найдено")
    void cleanupOfForeignVaultThrowsException() throws Exception {
        Long emptyBullionId = createBullion(vaultId, bullionNameId1, "0");

        deleteEmptyBullions(otherAccessToken, vaultId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4004));

        assertThat(bullionRow(emptyBullionId).get("archived")).isEqualTo(false);
    }

    @Test
    @DisplayName("Уборка в несуществующем хранилище - ошибка")
    void cleanupOfMissingVaultThrowsException() throws Exception {
        deleteEmptyBullions(accessToken, 99999L)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4004));
    }

    @Test
    @DisplayName("Ликвидный резерв чистится уборкой и после неё удаляется")
    void liquidityReserveIsCleanedAndThenDeleted() throws Exception {
        Long reserveId = createVault("Ликвидный резерв", "LIQUIDITY_BUFFER");
        Long emptyBullionId = createBullion(reserveId, bullionNameId1, "0");

        deleteEmptyBullions(accessToken, reserveId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));

        mockMvc.perform(delete("/api/vaults/{vaultId}", reserveId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        assertThat(bullionRow(emptyBullionId).get("archived")).isEqualTo(true);
        assertThat(vaultArchived(reserveId)).isTrue();
    }

    private ResultActions deleteEmptyBullions(String token, Long vault) throws Exception {
        return mockMvc.perform(delete("/api/vaults/{vaultId}/empty-bullions", vault)
                .header("Authorization", "Bearer " + token));
    }

    private String registerAndLogin(String email) throws Exception {
        String registerRequest = """
                {
                    "email": "%s",
                    "password": "Test123%%",
                    "fullName": "Empty Bullions User"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));

        User user = userRepository.findByEmail(email).orElseThrow();

        mockMvc.perform(get("/api/auth/verify")
                .param("token", user.getVerificationToken()));

        String loginRequest = """
                {
                    "email": "%s",
                    "password": "Test123%%"
                }
                """.formatted(email);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("data").get("token").asText();
    }

    private Long createVault(String name, String vaultType) throws Exception {
        String request = vaultType == null
                ? """
                {
                    "name": "%s"
                }
                """.formatted(name)
                : """
                {
                    "name": "%s",
                    "vaultType": "%s"
                }
                """.formatted(name, vaultType);

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

    private Long createBullion(Long vault, Long bullionNameId, String amount) throws Exception {
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

    /** Счётчик того самого хранилища из списка, каким его видит экран хранилищ. */
    private int emptyBullionsCountFromList() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        for (JsonNode vault : objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("content")) {
            if (vault.get("id").asLong() == vaultId) {
                return vault.get("emptyBullionsCount").asInt();
            }
        }
        throw new AssertionError("хранилище " + vaultId + " пропало из списка");
    }

    private Long dataId(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("data").get("id").asLong();
    }

    private Map<String, Object> bullionRow(Long bullionId) {
        return jdbcTemplate.queryForMap(
                "SELECT amount, archived, vault_id FROM taurus.bullions WHERE id = ?", bullionId);
    }

    private boolean vaultArchived(Long vault) {
        Boolean archived = jdbcTemplate.queryForObject(
                "SELECT archived FROM taurus.vaults WHERE id = ?", Boolean.class, vault);
        return Boolean.TRUE.equals(archived);
    }

    private int transactionCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM taurus.transactions", Integer.class);
        return count == null ? 0 : count;
    }
}
