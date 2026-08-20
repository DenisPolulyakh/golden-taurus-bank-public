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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Тип слитка: дебетовый (свои накопления) или кредитный (заёмные средства).
 *
 * <p>На расчёты поле пока не влияет — проверяется только то, что тип
 * выставляется при создании, правится карандашом и доезжает до всех
 * представлений слитка. Умолчание — {@code DEBIT}: тип появился позже самих
 * слитков, и запрос без поля должен работать как раньше.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Интеграционные тесты типа слитка")
class BullionTypeIntegrationTest extends IntegrationTestBase {

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
    private Long vaultId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "email": "bulliontype@example.com",
                            "password": "Test123%",
                            "fullName": "Bullion Type User"
                        }
                        """));

        User user = userRepository.findByEmail("bulliontype@example.com").get();
        mockMvc.perform(get("/api/auth/verify").param("token", user.getVerificationToken()));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "bulliontype@example.com",
                                    "password": "Test123%"
                                }
                                """))
                .andReturn();
        accessToken = json(loginResult).get("data").get("token").asText();

        goldId = createBullionName("Золото");
        vaultId = createVault("Хранилище");
    }

    @Test
    @DisplayName("Слиток без указанного типа создаётся дебетовым")
    void defaultsToDebit() throws Exception {
        Long bullionId = createBullion(null);

        assertEquals("DEBIT", bullion(bullionId).get("bullionType").asText());
    }

    @Test
    @DisplayName("Слиток можно создать кредитным")
    void createsCredit() throws Exception {
        Long bullionId = createBullion("CREDIT");

        assertEquals("CREDIT", bullion(bullionId).get("bullionType").asText());
    }

    @Test
    @DisplayName("Правка слитка меняет тип на кредитный")
    void updateSwitchesType() throws Exception {
        Long bullionId = createBullion("DEBIT");

        update(bullionId, "\"bullionType\": \"CREDIT\",");

        assertEquals("CREDIT", bullion(bullionId).get("bullionType").asText());
    }

    @Test
    @DisplayName("Правка без поля тип не сбрасывает")
    void updateWithoutTypeKeepsIt() throws Exception {
        Long bullionId = createBullion("CREDIT");

        update(bullionId, "");

        assertEquals("CREDIT", bullion(bullionId).get("bullionType").asText());
    }

    @Test
    @DisplayName("Тип виден в сводке хранилища - оттуда его берёт форма правки")
    void typeVisibleInVaultSummary() throws Exception {
        createBullion("CREDIT");

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/vaults/" + vaultId + "/summary")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bullions[0].bullionType").value("CREDIT"));
    }

    // Вспомогательные методы

    private JsonNode bullion(Long bullionId) throws Exception {
        entityManager.flush();
        entityManager.clear();

        MvcResult result = mockMvc.perform(get("/api/bullions/" + bullionId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("data");
    }

    /** {@code typeLine} — уже готовая строка тела запроса либо пустая, если поле не шлём. */
    private void update(Long bullionId, String typeLine) throws Exception {
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(put("/api/bullions/" + bullionId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "bullionNameId": %d,
                                    "vaultId": %d,
                                    "amount": 100000,
                                    %s
                                    "description": "Правка"
                                }
                                """.formatted(goldId, vaultId, typeLine)))
                .andExpect(status().isOk());
    }

    private Long createBullion(String bullionType) throws Exception {
        String typeLine = bullionType == null ? "" : "\"bullionType\": \"%s\",".formatted(bullionType);
        MvcResult result = mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "bullionNameId": %d,
                                    "vaultId": %d,
                                    %s
                                    "amount": 100000,
                                    "dateOperation": "2026-07-20T12:00:00"
                                }
                                """.formatted(goldId, vaultId, typeLine)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("data").get("id").asLong();
    }

    private Long createVault(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "%s"
                                }
                                """.formatted(name)))
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
