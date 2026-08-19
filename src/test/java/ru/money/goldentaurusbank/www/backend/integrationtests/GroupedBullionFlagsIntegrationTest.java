package ru.money.goldentaurusbank.www.backend.integrationtests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Кнопки сгруппированной карточки слитка и запреты операций на сервере.
 *
 * <p>Правила считает бэк, фронт только гасит по ним кнопки:
 * <ul>
 *     <li>операция доступна группе, пока её разрешает хоть одно хранилище наименования;</li>
 *     <li>перевод из хранилища = снятие оттуда, перевод в хранилище = внесение туда,
 *     поэтому «переводить можно», но «снимать нельзя» — это запрет на перевод;</li>
 *     <li>гашение кнопки — подсказка, запрос в обход интерфейса падает с ошибкой.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Интеграционные тесты кнопок сгруппированного слитка")
class GroupedBullionFlagsIntegrationTest extends IntegrationTestBase {

    private static final int VAULT_INCOME_NOT_ALLOWED = 4015;
    private static final int VAULT_EXPENSE_NOT_ALLOWED = 4016;
    private static final int VAULT_TRANSFER_NOT_ALLOWED = 4017;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private String accessToken;
    private Long goldId;
    private Long silverId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "email": "groupflags@example.com",
                            "password": "Test123%",
                            "fullName": "Group Flags User"
                        }
                        """));

        User user = userRepository.findByEmail("groupflags@example.com").get();
        mockMvc.perform(get("/api/auth/verify").param("token", user.getVerificationToken()));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "groupflags@example.com",
                                    "password": "Test123%"
                                }
                                """))
                .andReturn();
        accessToken = json(loginResult).get("data").get("token").asText();

        goldId = createBullionName("Золото");
        silverId = createBullionName("Серебро");
    }

    @Test
    @DisplayName("Все хранилища наименования закрыты - кнопки группы погашены")
    void groupFlagsFalseWhenEveryVaultForbids() throws Exception {
        Long lockedOne = createVault("Заперто раз", false, false, false);
        Long lockedTwo = createVault("Заперто два", false, false, false);
        createBullion(goldId, lockedOne, "100000");
        createBullion(goldId, lockedTwo, "50000");
        // Открытый слиток другого наименования: получатель для перевода есть,
        // и всё равно переводить не из чего
        createBullion(silverId, createVault("Открыто", true, true, true), "10000");

        JsonNode group = group("Золото");
        assertFalse(group.get("allowedIncome").asBoolean(), "внести некуда");
        assertFalse(group.get("allowedExpense").asBoolean(), "снять неоткуда");
        assertFalse(group.get("allowedTransfer").asBoolean(), "переводить не из чего");
    }

    @Test
    @DisplayName("Хоть одно хранилище разрешает - кнопки группы живые")
    void groupFlagsTrueWhenAnyVaultAllows() throws Exception {
        Long locked = createVault("Заперто", false, false, false);
        Long open = createVault("Открыто", true, true, true);
        createBullion(goldId, locked, "100000");
        createBullion(goldId, open, "50000");
        // Получатель перевода: слиток вне группы, в хранилище с разрешённым внесением
        createBullion(silverId, createVault("Приёмник", true, true, true), "10000");

        JsonNode group = group("Золото");
        assertTrue(group.get("allowedIncome").asBoolean());
        assertTrue(group.get("allowedExpense").asBoolean());
        assertTrue(group.get("allowedTransfer").asBoolean());

        JsonNode lockedVault = vault(group, "Заперто");
        assertFalse(lockedVault.get("allowedIncome").asBoolean());
        assertFalse(lockedVault.get("allowedTransferOut").asBoolean());
        assertFalse(lockedVault.get("allowedTransferIn").asBoolean());

        JsonNode openVault = vault(group, "Открыто");
        assertTrue(openVault.get("allowedTransferOut").asBoolean());
        assertTrue(openVault.get("allowedTransferIn").asBoolean());
    }

    @Test
    @DisplayName("Снимать нельзя - значит и переводить нельзя")
    void transferFollowsExpenseFlag() throws Exception {
        // Галочка «Можно переводить» стоит, но снятие запрещено
        Long noExpense = createVault("Только пополнение", true, false, true);
        createBullion(goldId, noExpense, "100000");
        createBullion(silverId, createVault("Приёмник", true, true, true), "10000");

        JsonNode group = group("Золото");
        assertFalse(group.get("allowedTransfer").asBoolean(), "перевод = снятие, а снимать нельзя");
        assertTrue(group.get("allowedIncome").asBoolean());

        JsonNode vault = vault(group, "Только пополнение");
        assertFalse(vault.get("allowedTransferOut").asBoolean());
        assertTrue(vault.get("allowedTransferIn").asBoolean(), "внести переводом сюда можно");
    }

    @Test
    @DisplayName("Вносить нельзя - хранилище не показывается как получатель перевода")
    void transferInFollowsIncomeFlag() throws Exception {
        Long noIncome = createVault("Только снятие", false, true, true);
        createBullion(goldId, noIncome, "100000");

        JsonNode vault = vault(group("Золото"), "Только снятие");
        assertFalse(vault.get("allowedTransferIn").asBoolean());
        assertTrue(vault.get("allowedTransferOut").asBoolean());
    }

    @Test
    @DisplayName("Переводить некуда - кнопка перевода погашена")
    void transferFalseWithoutTargets() throws Exception {
        Long open = createVault("Единственное", true, true, true);
        createBullion(goldId, open, "100000");

        // Слиток в группе один, получателей больше нет: сам себе не отправишь
        assertFalse(group("Золото").get("allowedTransfer").asBoolean());
    }

    @Test
    @DisplayName("Сводка отдаёт id слитка - перевод адресуется слитку, а не хранилищу")
    void groupedCarriesBullionId() throws Exception {
        Long vaultId = createVault("Хранилище", true, true, true);
        Long bullionId = createBullion(goldId, vaultId, "100000");

        JsonNode vault = vault(group("Золото"), "Хранилище");
        assertNotNull(vault.get("bullionId"));
        assertEquals(bullionId, vault.get("bullionId").asLong());
    }

    @Test
    @DisplayName("Внесение в хранилище со снятой галочкой отклоняется сервером")
    void refillRejectedWhenIncomeForbidden() throws Exception {
        Long vaultId = createVault("Без пополнения", false, true, true);
        createBullion(goldId, vaultId, "100000");

        mockMvc.perform(post("/api/bullions/refill")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "bullionNameId": %d,
                                    "vaultId": %d,
                                    "amount": 1000
                                }
                                """.formatted(goldId, vaultId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(VAULT_INCOME_NOT_ALLOWED));
    }

    @Test
    @DisplayName("Снятие из хранилища со снятой галочкой отклоняется сервером")
    void withdrawRejectedWhenExpenseForbidden() throws Exception {
        Long vaultId = createVault("Без снятия", true, false, true);
        createBullion(goldId, vaultId, "100000");

        mockMvc.perform(post("/api/bullions/withdraw")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "bullionNameId": %d,
                                    "vaultId": %d,
                                    "amount": 1000
                                }
                                """.formatted(goldId, vaultId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(VAULT_EXPENSE_NOT_ALLOWED));
    }

    @Test
    @DisplayName("Перевод из хранилища без снятия отклоняется сервером")
    void transferRejectedWhenSourceExpenseForbidden() throws Exception {
        Long from = createVault("Источник без снятия", true, false, true);
        Long to = createVault("Приёмник", true, true, true);
        Long fromBullion = createBullion(goldId, from, "100000");
        Long toBullion = createBullion(silverId, to, "1000");

        transfer(fromBullion, toBullion)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(VAULT_EXPENSE_NOT_ALLOWED));
    }

    @Test
    @DisplayName("Перевод в хранилище без пополнения отклоняется сервером")
    void transferRejectedWhenTargetIncomeForbidden() throws Exception {
        Long from = createVault("Источник", true, true, true);
        Long to = createVault("Приёмник без пополнения", false, true, true);
        Long fromBullion = createBullion(goldId, from, "100000");
        Long toBullion = createBullion(silverId, to, "1000");

        transfer(fromBullion, toBullion)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(VAULT_INCOME_NOT_ALLOWED));
    }

    @Test
    @DisplayName("Перевод при снятой галочке перевода отклоняется сервером")
    void transferRejectedWhenTransferForbidden() throws Exception {
        Long from = createVault("Источник без перевода", true, true, false);
        Long to = createVault("Приёмник", true, true, true);
        Long fromBullion = createBullion(goldId, from, "100000");
        Long toBullion = createBullion(silverId, to, "1000");

        transfer(fromBullion, toBullion)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(VAULT_TRANSFER_NOT_ALLOWED));
    }

    @Test
    @DisplayName("Разрешённый перевод проходит")
    void transferPassesWhenAllowed() throws Exception {
        Long from = createVault("Источник", true, true, true);
        Long to = createVault("Приёмник", true, true, true);
        Long fromBullion = createBullion(goldId, from, "100000");
        Long toBullion = createBullion(silverId, to, "1000");

        transfer(fromBullion, toBullion).andExpect(status().isOk());
    }

    // Вспомогательные методы

    private org.springframework.test.web.servlet.ResultActions transfer(Long fromBullionId, Long toBullionId) throws Exception {
        entityManager.flush();
        entityManager.clear();
        return mockMvc.perform(post("/api/bullions/transfer")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "fromBullionId": %d,
                            "toBullionId": %d,
                            "amount": 500
                        }
                        """.formatted(fromBullionId, toBullionId)));
    }

    private JsonNode group(String bullionNameTitle) throws Exception {
        entityManager.flush();
        entityManager.clear();

        MvcResult result = mockMvc.perform(get("/api/bullions/grouped")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        for (JsonNode group : json(result).get("data").get("bullionNameBullionList")) {
            if (bullionNameTitle.equals(group.get("bullionNameTitle").asText())) {
                return group;
            }
        }
        throw new AssertionError("В сводке нет наименования " + bullionNameTitle);
    }

    private JsonNode vault(JsonNode group, String vaultName) {
        for (JsonNode vault : group.get("vaults")) {
            if (vaultName.equals(vault.get("name").asText())) {
                return vault;
            }
        }
        throw new AssertionError("В наименовании нет хранилища " + vaultName);
    }

    private Long createVault(String name, boolean income, boolean expense, boolean transfer) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "%s",
                                    "allowedIncome": %b,
                                    "allowedExpense": %b,
                                    "allowedTransfer": %b
                                }
                                """.formatted(name, income, expense, transfer)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("data").get("id").asLong();
    }

    private Long createBullion(Long bullionNameId, Long vaultId, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "bullionNameId": %d,
                                    "vaultId": %d,
                                    "amount": %s,
                                    "dateOperation": "2026-07-20T12:00:00"
                                }
                                """.formatted(bullionNameId, vaultId, amount)))
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
