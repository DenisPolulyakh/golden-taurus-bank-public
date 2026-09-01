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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Интеграционные тесты удаления хранилища")
class VaultDeleteIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String accessToken;
    private Long userId;
    private Long bullionNameId1;
    private Long bullionNameId2;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE taurus.transactions, taurus.bullions, taurus.vaults, "
                + "taurus.bullion_names, taurus.bank_dictionary, taurus.users RESTART IDENTITY CASCADE");

        String registerRequest = """
                {
                    "email": "vaultdelete@example.com",
                    "password": "Test123%",
                    "fullName": "Vault Delete User"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));

        User user = userRepository.findByEmail("vaultdelete@example.com").orElseThrow();
        userId = user.getId();

        mockMvc.perform(get("/api/auth/verify")
                .param("token", user.getVerificationToken()));

        String loginRequest = """
                {
                    "email": "vaultdelete@example.com",
                    "password": "Test123%"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andReturn();

        accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("data").get("token").asText();

        bullionNameId1 = createBullionName("Финансовая подушка");
        bullionNameId2 = createBullionName("Накопления");
    }

    @Test
    @DisplayName("Удаление хранилища со слитком с остатком - слиток уходит в ликвидное хранилище")
    void deleteVaultMovesFundedBullionToLiquidityReserve() throws Exception {
        Long vaultId = createVault("Сбербанк", null);
        Long bullionId = createBullion(vaultId, bullionNameId1, "1000.00");

        deleteVault(vaultId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(vaultArchived(vaultId)).isTrue();

        Long reserveId = liquidityReserveId();
        assertThat(reserveId).isNotNull();
        assertThat(activeAmountInVault(reserveId)).isEqualByComparingTo("1000.00");

        Map<String, Object> source = bullionRow(bullionId);
        assertThat(source.get("archived")).isEqualTo(true);
        // Архивный слиток остаётся в архивном хранилище — иначе история потеряет его название
        assertThat(source.get("vault_id")).isEqualTo(vaultId);
        assertThat((BigDecimal) source.get("amount")).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Удаление хранилища со слитками с нулевым остатком - слитки архивируются, ликвидное хранилище не создаётся")
    void deleteVaultArchivesEmptyBullionsWithoutReserve() throws Exception {
        Long vaultId = createVault("Тинькофф", null);
        Long bullionId1 = createBullion(vaultId, bullionNameId1, "0");
        Long bullionId2 = createBullion(vaultId, bullionNameId2, "0");

        deleteVault(vaultId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(vaultArchived(vaultId)).isTrue();
        assertThat(liquidityReserveId()).isNull();

        for (Long bullionId : List.of(bullionId1, bullionId2)) {
            Map<String, Object> row = bullionRow(bullionId);
            assertThat(row.get("archived")).isEqualTo(true);
            assertThat(row.get("vault_id")).isEqualTo(vaultId);
        }

        assertThat(transactionCount()).isZero();
    }

    @Test
    @DisplayName("Удаление хранилища со смешанными слитками - с остатком в резерв, пустой в архив")
    void deleteVaultSplitsFundedAndEmptyBullions() throws Exception {
        Long vaultId = createVault("Альфа", null);
        Long fundedId = createBullion(vaultId, bullionNameId1, "750.50");
        Long emptyId = createBullion(vaultId, bullionNameId2, "0");

        deleteVault(vaultId)
                .andExpect(status().isOk());

        assertThat(vaultArchived(vaultId)).isTrue();

        Long reserveId = liquidityReserveId();
        assertThat(reserveId).isNotNull();
        assertThat(activeAmountInVault(reserveId)).isEqualByComparingTo("750.50");
        assertThat(activeBullionCount(reserveId)).isEqualTo(1);

        assertThat(bullionRow(fundedId).get("archived")).isEqualTo(true);
        assertThat(bullionRow(fundedId).get("vault_id")).isEqualTo(vaultId);
        assertThat(bullionRow(emptyId).get("archived")).isEqualTo(true);
        assertThat(bullionRow(emptyId).get("vault_id")).isEqualTo(vaultId);
    }

    @Test
    @DisplayName("Удаление ликвидного хранилища со слитком с остатком - предупреждение")
    void deleteLiquidityReserveWithFundedBullionThrowsException() throws Exception {
        Long reserveId = createVault("Ликвидный резерв", "LIQUIDITY_BUFFER");
        Long bullionId = createBullion(reserveId, bullionNameId1, "500.00");

        deleteVault(reserveId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4014))
                .andExpect(jsonPath("$.message")
                        .value("В ликвидном хранилище есть слитки с остатком. Сначала перенесите их в другое хранилище"));

        assertThat(vaultArchived(reserveId)).isFalse();

        Map<String, Object> row = bullionRow(bullionId);
        assertThat(row.get("archived")).isEqualTo(false);
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("Удаление ликвидного хранилища без остатков - успешно")
    void deleteLiquidityReserveWithoutFundedBullionsSuccess() throws Exception {
        Long reserveId = createVault("Ликвидный резерв", "LIQUIDITY_BUFFER");
        Long bullionId = createBullion(reserveId, bullionNameId1, "0");

        deleteVault(reserveId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(vaultArchived(reserveId)).isTrue();
        assertThat(bullionRow(bullionId).get("archived")).isEqualTo(true);
        assertThat(bullionRow(bullionId).get("vault_id")).isEqualTo(reserveId);
    }

    @Test
    @DisplayName("Удаление хранилища с архивным слитком - успешно")
    void deleteVaultWithArchivedBullionSuccess() throws Exception {
        Long vaultId = createVault("ВТБ", null);
        Long bullionId = createBullion(vaultId, bullionNameId1, "0");

        mockMvc.perform(delete("/api/bullions/{bullionId}", bullionId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        assertThat(bullionRow(bullionId).get("archived")).isEqualTo(true);

        deleteVault(vaultId)
                .andExpect(status().isOk());

        assertThat(vaultArchived(vaultId)).isTrue();
        assertThat(bullionRow(bullionId).get("vault_id")).isEqualTo(vaultId);
    }

    @Test
    @DisplayName("Удаление пустого хранилища - успешно")
    void deleteEmptyVaultSuccess() throws Exception {
        Long vaultId = createVault("Пустое", null);

        deleteVault(vaultId)
                .andExpect(status().isOk());

        assertThat(vaultArchived(vaultId)).isTrue();
        assertThat(liquidityReserveId()).isNull();
    }

    @Test
    @DisplayName("Слитки резерва удалены руками, затем удаляется и сам резерв")
    void deleteLiquidityReserveAfterEmptyingBullionsByHand() throws Exception {
        Long reserveId = createVault("Ликвидный резерв", "LIQUIDITY_BUFFER");
        Long firstBullionId = createBullion(reserveId, bullionNameId1, "0");
        Long secondBullionId = createBullion(reserveId, bullionNameId2, "0");

        deleteBullion(firstBullionId).andExpect(status().isOk());
        deleteBullion(secondBullionId).andExpect(status().isOk());

        deleteVault(reserveId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(vaultArchived(reserveId)).isTrue();
        assertThat(bullionRow(firstBullionId).get("archived")).isEqualTo(true);
        assertThat(bullionRow(secondBullionId).get("archived")).isEqualTo(true);
    }

    @Test
    @DisplayName("После удаления резерва следующий перенос остатка поднимает новый")
    void liquidityReserveIsRecreatedAfterDeletion() throws Exception {
        Long reserveId = createVault("Ликвидный резерв", "LIQUIDITY_BUFFER");
        deleteVault(reserveId).andExpect(status().isOk());
        assertThat(activeLiquidityReserveId()).isNull();

        // Остаток переносить некуда - резерв должен завестись заново, а не уронить удаление
        Long vaultId = createVault("Сбербанк", null);
        Long bullionId = createBullion(vaultId, bullionNameId1, "700.00");

        deleteVault(vaultId).andExpect(status().isOk());

        Long newReserveId = activeLiquidityReserveId();
        assertThat(newReserveId).isNotNull().isNotEqualTo(reserveId);
        assertThat(bullionRow(bullionId).get("archived")).isEqualTo(true);
        assertThat(amountInVault(newReserveId)).isEqualByComparingTo("700.00");
    }

    @Test
    @DisplayName("Удаление несуществующего хранилища - ошибка")
    void deleteVaultNotFoundThrowsException() throws Exception {
        deleteVault(99999L)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4004));
    }

    private ResultActions deleteBullion(Long bullionId) throws Exception {
        return mockMvc.perform(delete("/api/bullions/{bullionId}", bullionId)
                .header("Authorization", "Bearer " + accessToken));
    }

    private ResultActions deleteVault(Long vaultId) throws Exception {
        return mockMvc.perform(delete("/api/vaults/{vaultId}", vaultId)
                .header("Authorization", "Bearer " + accessToken));
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

    private Long createBullion(Long vaultId, Long bullionNameId, String amount) throws Exception {
        String request = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": %s
                }
                """.formatted(bullionNameId, vaultId, amount);

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

    /**
     * Хранилище не удаляется физически, а архивируется: строка остаётся ради
     * истории операций, но для пользователя хранилища больше нет.
     */
    private boolean vaultArchived(Long vaultId) {
        List<Boolean> flags = jdbcTemplate.queryForList(
                "SELECT archived FROM taurus.vaults WHERE id = ?", Boolean.class, vaultId);
        assertThat(flags).as("строка хранилища должна остаться в БД").hasSize(1);
        return Boolean.TRUE.equals(flags.get(0));
    }

    private Long liquidityReserveId() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM taurus.vaults WHERE user_id = ? AND vault_type = 'LIQUIDITY_BUFFER'",
                Long.class, userId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** Архивный резерв в БД остаётся, поэтому по типу мало - нужен именно живой. */
    private Long activeLiquidityReserveId() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM taurus.vaults "
                        + "WHERE user_id = ? AND vault_type = 'LIQUIDITY_BUFFER' AND NOT archived",
                Long.class, userId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private BigDecimal amountInVault(Long vaultId) {
        List<BigDecimal> amounts = jdbcTemplate.queryForList(
                "SELECT amount FROM taurus.bullions WHERE vault_id = ? AND NOT archived",
                BigDecimal.class, vaultId);
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, Object> bullionRow(Long bullionId) {
        return jdbcTemplate.queryForMap(
                "SELECT amount, archived, vault_id FROM taurus.bullions WHERE id = ?", bullionId);
    }

    private BigDecimal activeAmountInVault(Long vaultId) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM taurus.bullions WHERE vault_id = ? AND NOT archived",
                BigDecimal.class, vaultId);
    }

    private int activeBullionCount(Long vaultId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM taurus.bullions WHERE vault_id = ? AND NOT archived",
                Integer.class, vaultId);
        return count == null ? 0 : count;
    }

    private int transactionCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM taurus.transactions WHERE user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }
}
