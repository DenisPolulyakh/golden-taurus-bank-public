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
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.repository.TransactionRepository;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Перевод можно адресовать хранилищу, а не слитку: если слитка нужного
 * наименования там нет, он заводится пустым и тут же принимает сумму.
 *
 * <p>Класс намеренно <b>без</b> {@code @Transactional}: один из тестов проверяет,
 * что запрещённый перевод откатывает и создание слитка, а в общей с тестом
 * транзакции откат не наблюдаем — сервис лишь пометил бы её rollback-only.
 * Чистоту базы обеспечивает {@code TRUNCATE} из {@link IntegrationTestBase}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Интеграционные тесты перевода в хранилище без слитка")
class TransferToNewBullionIntegrationTest extends IntegrationTestBase {

    private static final int TRANSFER_TARGET_NOT_SET = 4039;
    private static final int VAULT_INCOME_NOT_ALLOWED = 4015;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private String accessToken;
    private Long goldId;
    private Long fromVaultId;
    private Long toVaultId;
    private Long fromBullionId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "email": "transfernew@example.com",
                            "password": "Test123%",
                            "fullName": "Transfer New User"
                        }
                        """));

        User user = userRepository.findByEmail("transfernew@example.com").get();
        mockMvc.perform(get("/api/auth/verify").param("token", user.getVerificationToken()));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "transfernew@example.com",
                                    "password": "Test123%"
                                }
                                """))
                .andReturn();
        accessToken = json(loginResult).get("data").get("token").asText();

        goldId = createBullionName("Золото");
        fromVaultId = createVault("Откуда", "");
        toVaultId = createVault("Куда", "");
        fromBullionId = createBullion(fromVaultId, "100000");
    }

    @Test
    @DisplayName("Слитка в хранилище нет - он создаётся и принимает перевод")
    void createsBullionInTargetVault() throws Exception {
        long transactionsBefore = transactionRepository.count();

        transferToVault(toVaultId, "30000").andExpect(status().isOk());

        assertEquals(70000, amountInVault(fromVaultId).intValue());

        JsonNode created = bullionInVault(toVaultId);
        assertNotNull(created, "слиток должен был появиться в целевом хранилище");
        assertEquals(30000, created.get("amount").decimalValue().intValue());
        assertEquals("Золото", created.get("bullionName").get("title").asText(),
                "наименование берётся у отправителя");
        assertEquals("DEBIT", created.get("bullionType").asText(), "тип берётся у отправителя");

        assertEquals(transactionsBefore + 1, transactionRepository.count(),
                "перевод - одна операция: стартовый остаток на ноль не пишется");
    }

    @Test
    @DisplayName("Слиток в хранилище уже есть - деньги приходят в него, дубля нет")
    void reusesExistingBullion() throws Exception {
        Long existingId = createBullion(toVaultId, "5000");

        transferToVault(toVaultId, "30000").andExpect(status().isOk());

        assertEquals(1, bullionsInVault(toVaultId), "второй слиток того же наименования не заводится");
        JsonNode existing = bullionInVault(toVaultId);
        assertEquals(existingId.longValue(), existing.get("id").asLong());
        assertEquals(35000, existing.get("amount").decimalValue().intValue());
    }

    @Test
    @DisplayName("Архивный слиток оживает вместе со своей историей")
    void revivesArchivedBullion() throws Exception {
        Long archivedId = createBullion(toVaultId, "0");
        mockMvc.perform(delete("/api/bullions/{bullionId}", archivedId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        assertNull(bullionInVault(toVaultId), "архивный слиток из списка пропал");

        transferToVault(toVaultId, "30000").andExpect(status().isOk());

        JsonNode revived = bullionInVault(toVaultId);
        assertNotNull(revived, "слиток вернулся в список");
        assertEquals(archivedId.longValue(), revived.get("id").asLong(),
                "пара «наименование + хранилище» уникальна - оживает прежний слиток, а не заводится новый");
        assertEquals(30000, revived.get("amount").decimalValue().intValue());
    }

    @Test
    @DisplayName("В хранилище вносить нельзя - перевод падает и слитка не остаётся")
    void rejectsVaultWithoutIncome() throws Exception {
        Long lockedVaultId = createVault("Только снятие", """
                ,
                "allowedIncome": false
                """);

        transferToVault(lockedVaultId, "30000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(VAULT_INCOME_NOT_ALLOWED));

        assertNull(bullionInVault(lockedVaultId),
                "создание слитка откатилось вместе с запрещённым переводом");
        assertEquals(100000, amountInVault(fromVaultId).intValue(), "у отправителя ничего не ушло");
    }

    @Test
    @DisplayName("Получатель задан обоими полями или ни одним - ошибка")
    void rejectsAmbiguousTarget() throws Exception {
        Long toBullionId = createBullion(toVaultId, "5000");

        transfer("""
                {
                    "fromBullionId": %d,
                    "toBullionId": %d,
                    "toVaultId": %d,
                    "amount": 30000
                }
                """.formatted(fromBullionId, toBullionId, toVaultId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(TRANSFER_TARGET_NOT_SET));

        transfer("""
                {
                    "fromBullionId": %d,
                    "amount": 30000
                }
                """.formatted(fromBullionId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(TRANSFER_TARGET_NOT_SET));

        assertEquals(100000, amountInVault(fromVaultId).intValue(), "ни один запрос денег не двинул");
    }

    // Вспомогательные методы

    private ResultActions transferToVault(Long vaultId, String amount) throws Exception {
        return transfer("""
                {
                    "fromBullionId": %d,
                    "toVaultId": %d,
                    "amount": %s,
                    "comment": "Перевод в новое хранилище"
                }
                """.formatted(fromBullionId, vaultId, amount));
    }

    private ResultActions transfer(String body) throws Exception {
        return mockMvc.perform(post("/api/bullions/transfer")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** Живые слитки хранилища: архивные в списке не показываются. */
    private JsonNode bullionInVault(Long vaultId) throws Exception {
        for (JsonNode bullion : bullions()) {
            if (bullion.get("vault").get("id").asLong() == vaultId) {
                return bullion;
            }
        }
        return null;
    }

    private int bullionsInVault(Long vaultId) throws Exception {
        int count = 0;
        for (JsonNode bullion : bullions()) {
            if (bullion.get("vault").get("id").asLong() == vaultId) {
                count++;
            }
        }
        return count;
    }

    private BigDecimal amountInVault(Long vaultId) throws Exception {
        JsonNode bullion = bullionInVault(vaultId);
        assertNotNull(bullion, "слиток в хранилище " + vaultId + " не найден");
        return bullion.get("amount").decimalValue();
    }

    private JsonNode bullions() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("data");
    }

    private Long createBullion(Long vaultId, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "bullionNameId": %d,
                                    "vaultId": %d,
                                    "amount": %s,
                                    "dateOperation": "2026-08-20T12:00:00"
                                }
                                """.formatted(goldId, vaultId, amount)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("data").get("id").asLong();
    }

    /** {@code extraFields} — уже с ведущей запятой, например галочки хранилища. */
    private Long createVault(String name, String extraFields) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "%s"%s
                                }
                                """.formatted(name, extraFields)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("data").get("id").asLong();
    }

    private Long createBullionName(String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/bullion-names")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "%s"
                                }
                                """.formatted(title)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("data").get("id").asLong();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
