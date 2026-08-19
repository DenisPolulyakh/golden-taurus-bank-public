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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Галочки операций у хранилища (plans/PLAN_VAULT_ALLOW.md).
 *
 * <p>Правило одно: разрешённость операции задаёт только галочка. Дата закрытия
 * срочного вклада справочная и ни на что не влияет — условия у вкладов разные,
 * галочки ставит пользователь.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Интеграционные тесты галочек операций хранилища")
class VaultAllowedFlagsIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private String accessToken;
    private Long bullionNameId;

    /** Дата закрытия в будущем — раньше она гасила операции, теперь не должна. */
    private static final LocalDate FUTURE = LocalDate.now().plusYears(1);

    @BeforeEach
    void setUp() throws Exception {
        String registerRequest = """
                {
                    "email": "allowuser@example.com",
                    "password": "Test123%",
                    "fullName": "Allow Test User"
                }
                """;
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));

        User user = userRepository.findByEmail("allowuser@example.com").get();
        mockMvc.perform(get("/api/auth/verify")
                .param("token", user.getVerificationToken()));

        String loginRequest = """
                {
                    "email": "allowuser@example.com",
                    "password": "Test123%"
                }
                """;
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andReturn();
        accessToken = json(loginResult).get("data").get("token").asText();

        MvcResult bullionNameResult = mockMvc.perform(post("/api/bullion-names")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Накопления"
                                }
                                """))
                .andReturn();
        bullionNameId = json(bullionNameResult).get("data").get("id").asLong();
    }

    @Test
    @DisplayName("Создание без новых полей - все галочки true")
    void createWithoutFlagsDefaultsToTrue() throws Exception {
        mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Обычное хранилище"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings.allowedIncome").value(true))
                .andExpect(jsonPath("$.data.settings.allowedExpense").value(true))
                .andExpect(jsonPath("$.data.settings.allowedTransfer").value(true))
                .andExpect(jsonPath("$.data.allowedIncome").value(true))
                .andExpect(jsonPath("$.data.allowedExpense").value(true))
                .andExpect(jsonPath("$.data.allowedTransfer").value(true));
    }

    @Test
    @DisplayName("Создание со снятой галочкой - false и в settings, и наверху")
    void createWithUncheckedExpense() throws Exception {
        Long vaultId = createVault("""
                {
                    "name": "Только пополнение",
                    "allowedExpense": false
                }
                """);

        getVault(vaultId)
                .andExpect(jsonPath("$.data.settings.allowedExpense").value(false))
                .andExpect(jsonPath("$.data.allowedExpense").value(false))
                .andExpect(jsonPath("$.data.settings.allowedIncome").value(true))
                .andExpect(jsonPath("$.data.allowedIncome").value(true))
                .andExpect(jsonPath("$.data.settings.allowedTransfer").value(true))
                .andExpect(jsonPath("$.data.allowedTransfer").value(true));
    }

    @Test
    @DisplayName("Правка снимает галочку - изменение сохраняется")
    void updateUnchecksFlag() throws Exception {
        Long vaultId = createVault("""
                {
                    "name": "Хранилище под правку"
                }
                """);

        mockMvc.perform(put("/api/vaults/{vaultId}", vaultId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Хранилище под правку",
                                    "allowedIncome": false,
                                    "allowedExpense": true,
                                    "allowedTransfer": false
                                }
                                """))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        getVault(vaultId)
                .andExpect(jsonPath("$.data.settings.allowedIncome").value(false))
                .andExpect(jsonPath("$.data.settings.allowedExpense").value(true))
                .andExpect(jsonPath("$.data.settings.allowedTransfer").value(false))
                .andExpect(jsonPath("$.data.allowedIncome").value(false))
                .andExpect(jsonPath("$.data.allowedTransfer").value(false));
    }

    @Test
    @DisplayName("Правка без новых полей - снятые галочки не возвращаются в true")
    void updateWithoutFlagsKeepsCurrentValues() throws Exception {
        Long vaultId = createVault("""
                {
                    "name": "Хранилище старого клиента",
                    "allowedIncome": false
                }
                """);

        // Старый клиент шлёт PUT без allowed*: значения должны остаться прежними
        mockMvc.perform(put("/api/vaults/{vaultId}", vaultId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Хранилище старого клиента",
                                    "description": "Новое описание"
                                }
                                """))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        getVault(vaultId)
                .andExpect(jsonPath("$.data.description").value("Новое описание"))
                .andExpect(jsonPath("$.data.settings.allowedIncome").value(false))
                .andExpect(jsonPath("$.data.allowedIncome").value(false));
    }

    @Test
    @DisplayName("Срочный вклад с будущей датой закрытия - операции разрешены")
    void termVaultWithFutureCloseDateStaysOpen() throws Exception {
        Long vaultId = createVault("""
                {
                    "name": "Срочный вклад",
                    "accountType": "TERM",
                    "closeDate": "%s"
                }
                """.formatted(FUTURE));

        getVault(vaultId)
                .andExpect(jsonPath("$.data.accountType").value("TERM"))
                .andExpect(jsonPath("$.data.settings.allowedIncome").value(true))
                .andExpect(jsonPath("$.data.settings.allowedExpense").value(true))
                .andExpect(jsonPath("$.data.settings.allowedTransfer").value(true))
                .andExpect(jsonPath("$.data.allowedIncome").value(true))
                .andExpect(jsonPath("$.data.allowedExpense").value(true))
                .andExpect(jsonPath("$.data.allowedTransfer").value(true))
                .andExpect(jsonPath("$.data.allowedChangeAmount").value(true))
                .andExpect(jsonPath("$.data.allowedEdit").value(true))
                .andExpect(jsonPath("$.data.allowedDelete").value(true));
    }

    @Test
    @DisplayName("Срочный вклад: запрещает только снятая галочка")
    void termVaultRespectsOnlyItsFlags() throws Exception {
        Long vaultId = createVault("""
                {
                    "name": "Вклад с пополнением два месяца",
                    "accountType": "TERM",
                    "closeDate": "%s",
                    "allowedExpense": false
                }
                """.formatted(FUTURE));

        getVault(vaultId)
                .andExpect(jsonPath("$.data.allowedExpense").value(false))
                .andExpect(jsonPath("$.data.allowedIncome").value(true))
                .andExpect(jsonPath("$.data.allowedTransfer").value(true));
    }

    @Test
    @DisplayName("Список слитков - галочки хранилища доезжают до слитка")
    void bullionListCarriesVaultFlags() throws Exception {
        Long vaultId = createVault("""
                {
                    "name": "Срочный со снятым снятием",
                    "accountType": "TERM",
                    "closeDate": "%s",
                    "allowedExpense": false
                }
                """.formatted(FUTURE));
        createBullion(vaultId);

        mockMvc.perform(get("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].vault.allowedExpense").value(false))
                .andExpect(jsonPath("$.data[0].vault.allowedIncome").value(true))
                .andExpect(jsonPath("$.data[0].vault.allowedTransfer").value(true))
                // константы маппера: без них правка суммы и удаление отвалились бы
                .andExpect(jsonPath("$.data[0].vault.allowedChangeAmount").value(true))
                .andExpect(jsonPath("$.data[0].vault.allowedEdit").value(true))
                .andExpect(jsonPath("$.data[0].vault.allowedDelete").value(true));
    }

    @Test
    @DisplayName("Сгруппированные слитки - галочки хранилища доезжают до слитка")
    void groupedBullionsCarryVaultFlags() throws Exception {
        Long vaultId = createVault("""
                {
                    "name": "Срочный со снятым переводом",
                    "accountType": "TERM",
                    "closeDate": "%s",
                    "allowedTransfer": false
                }
                """.formatted(FUTURE));
        createBullion(vaultId);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/bullions/grouped")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bullionNameBullionList[0].vaults[0].allowedTransfer").value(false))
                .andExpect(jsonPath("$.data.bullionNameBullionList[0].vaults[0].allowedIncome").value(true))
                .andExpect(jsonPath("$.data.bullionNameBullionList[0].vaults[0].allowedExpense").value(true))
                .andExpect(jsonPath("$.data.bullionNameBullionList[0].vaults[0].allowedChangeAmount").value(true))
                .andExpect(jsonPath("$.data.bullionNameBullionList[0].vaults[0].allowedEdit").value(true))
                .andExpect(jsonPath("$.data.bullionNameBullionList[0].vaults[0].allowedDelete").value(true));
    }

    // Вспомогательные методы
    private Long createVault(String requestBody) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        return json(result).get("data").get("id").asLong();
    }

    private void createBullion(Long vaultId) throws Exception {
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "bullionNameId": %d,
                                    "vaultId": %d,
                                    "amount": 100000,
                                    "dateOperation": "2026-07-20T12:00:00"
                                }
                                """.formatted(bullionNameId, vaultId)))
                .andExpect(status().isOk());
    }

    private ResultActions getVault(Long vaultId) throws Exception {
        return mockMvc.perform(get("/api/vaults/{vaultId}", vaultId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
