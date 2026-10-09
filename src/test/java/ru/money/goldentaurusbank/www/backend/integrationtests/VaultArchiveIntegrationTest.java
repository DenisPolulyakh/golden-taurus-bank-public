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
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Удалённое хранилище архивируется, а не исчезает: история операций должна
 * по-прежнему показывать банк и хранилище.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Интеграционные тесты архивации хранилища")
class VaultArchiveIntegrationTest extends IntegrationTestBase {

    private static final String OPERATION_DATE = "2026-08-19T12:00:00";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String accessToken;
    private Long bankId;
    private Long bullionNameId;

    @BeforeEach
    void setUp() throws Exception {
        String registerRequest = """
                {
                    "email": "vaultarchive@example.com",
                    "password": "Test123%",
                    "fullName": "Vault Archive User"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));

        User user = userRepository.findByEmail("vaultarchive@example.com").orElseThrow();
        mockMvc.perform(get("/api/auth/verify").param("token", user.getVerificationToken()));

        String loginRequest = """
                {
                    "email": "vaultarchive@example.com",
                    "password": "Test123%"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andReturn();

        accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("data").get("token").asText();

        bankId = createBank("Сбербанк");
        bullionNameId = createBullionName("Накопления");
    }

    @Test
    @DisplayName("После удаления хранилища история операций сохраняет банк и хранилище")
    void historyKeepsVaultAndBankAfterVaultDeleted() throws Exception {
        Long vaultId = createVault("Депозит", bankId);
        Long bullionId = createBullion(vaultId, bullionNameId, "1000");
        withdraw(vaultId, bullionNameId, 1000);

        deleteVault(vaultId).andExpect(status().isOk());

        // Слиток остался в архивном хранилище, а не потерял его
        assertThat(vaultIdOfBullion(bullionId)).isEqualTo(vaultId);

        JsonNode withdrawal = null;
        for (JsonNode tx : historyContent()) {
            if ("WITHDRAWAL".equals(tx.get("kind").asText())) {
                withdrawal = tx;
            }
        }
        assertThat(withdrawal).as("снятие должно быть в истории").isNotNull();

        List<String> texts = new ArrayList<>();
        boolean archivedSegment = false;
        for (JsonNode segment : withdrawal.get("descriptionSegments")) {
            texts.add(segment.get("text").asText());
            archivedSegment |= segment.get("archived").asBoolean();
        }

        assertThat(texts).contains("Сбербанк");
        assertThat(texts).contains("Депозит (удалено)");
        assertThat(archivedSegment).as("сегмент хранилища помечен архивным").isTrue();
        assertThat(withdrawal.get("description").asText()).contains("Сбербанк", "Депозит (удалено)");
    }

    @Test
    @DisplayName("Архивное хранилище не приходит в списке хранилищ")
    void archivedVaultIsHiddenFromList() throws Exception {
        Long vaultId = createVault("Депозит", bankId);
        createVault("Текущий", null);

        deleteVault(vaultId).andExpect(status().isOk());

        mockMvc.perform(get("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("Текущий"));
    }

    @Test
    @DisplayName("Слиток в архивном хранилище не создаётся")
    void bullionCannotBeCreatedInArchivedVault() throws Exception {
        Long vaultId = createVault("Депозит", bankId);
        deleteVault(vaultId).andExpect(status().isOk());

        String request = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 500
                }
                """.formatted(bullionNameId, vaultId);

        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4004));
    }

    @Test
    @DisplayName("Банк с хранилищем не удаляется — иначе история потеряет его название")
    void bankWithVaultIsNotDeleted() throws Exception {
        Long vaultId = createVault("Депозит", bankId);

        mockMvc.perform(delete("/api/banks/{bankId}", bankId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4018));

        // И после архивации хранилища банк по-прежнему держится
        deleteVault(vaultId).andExpect(status().isOk());

        mockMvc.perform(delete("/api/banks/{bankId}", bankId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4018));
    }

    // Вспомогательные методы

    private JsonNode historyContent() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/transactions/history")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
    }

    private Long vaultIdOfBullion(Long bullionId) {
        return jdbcTemplate.queryForObject(
                "SELECT vault_id FROM taurus.bullions WHERE id = ?", Long.class, bullionId);
    }

    private void withdraw(Long vaultId, Long bullionNameId, long amount) throws Exception {
        String request = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": %d,
                    "dateOperation": "%s"
                }
                """.formatted(bullionNameId, vaultId, amount, OPERATION_DATE);

        mockMvc.perform(post("/api/bullions/withdraw")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions deleteVault(Long vaultId) throws Exception {
        return mockMvc.perform(delete("/api/vaults/{vaultId}", vaultId)
                .header("Authorization", "Bearer " + accessToken));
    }

    private Long createVault(String name, Long bankId) throws Exception {
        String request = bankId == null
                ? """
                {
                    "name": "%s"
                }
                """.formatted(name)
                : """
                {
                    "name": "%s",
                    "bankId": %d
                }
                """.formatted(name, bankId);

        MvcResult result = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andReturn();

        return dataId(result);
    }

    private Long createBank(String name) throws Exception {
        String request = """
                {
                    "name": "%s"
                }
                """.formatted(name);

        MvcResult result = mockMvc.perform(post("/api/banks")
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
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }
}
