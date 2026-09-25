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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
 * История операций конкретного слитка: сортировка, пагинация по 10, поиск по
 * комментарию, «Остаток после» и видимость отката.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Интеграционные тесты истории слитка")
class BullionHistoryIntegrationTest extends IntegrationTestBase {

    private static final int BULLION_NOT_FOUND = 4006;
    private static final BigDecimal OPENING_1 = new BigDecimal("100000");
    private static final BigDecimal OPENING_2 = new BigDecimal("50000");
    private static final String OPENING_DATE = "2026-07-20T11:59:00";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private String accessToken;
    private Long bullionNameId1;
    private Long bullionNameId2;
    private Long vaultId1;
    private Long vaultId2;
    private Long bullionId1;
    private Long bullionId2;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "email": "bullionhistory@example.com",
                            "password": "Test123%",
                            "fullName": "Bullion History User"
                        }
                        """));

        User user = userRepository.findByEmail("bullionhistory@example.com").orElseThrow();
        mockMvc.perform(get("/api/auth/verify").param("token", user.getVerificationToken()));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "bullionhistory@example.com",
                                    "password": "Test123%"
                                }
                                """))
                .andReturn();
        accessToken = json(loginResult).get("data").get("token").asText();

        bullionNameId1 = createBullionName("Финансовая подушка");
        bullionNameId2 = createBullionName("Накопления");
        vaultId1 = createVault("Сбербанк");
        vaultId2 = createVault("Тинькофф");

        bullionId1 = createBullion(bullionNameId1, vaultId1, OPENING_1);
        bullionId2 = createBullion(bullionNameId2, vaultId2, OPENING_2);
    }

    // ==================== ТЕСТЫ ====================

    @Test
    @DisplayName("Операции слитка идут от новых к старым")
    void historyOrderedNewestFirst() throws Exception {
        performRefill(bullionNameId1, vaultId1, 10000, "2026-07-21T12:00:00", null);
        performWithdraw(bullionNameId1, vaultId1, 3000, "2026-07-22T10:00:00", null);
        performRefill(bullionNameId1, vaultId1, 5000, "2026-07-01T09:00:00", null);
        performRefill(bullionNameId1, vaultId1, 7000, "2026-07-22T10:00:00", null);

        JsonNode content = json(history(bullionId1, null, null, null)).get("data").get("content");

        assertThat(content).hasSize(5);
        assertThat(content.get(0).get("kind").asText()).isEqualTo("DEPOSIT");
        assertThat(bigDecimal(content.get(0), "amount")).isEqualByComparingTo(new BigDecimal("7000"));
        assertThat(content.get(1).get("kind").asText()).isEqualTo("WITHDRAWAL");
        assertThat(bigDecimal(content.get(1), "amount")).isEqualByComparingTo(new BigDecimal("3000"));
        assertThat(content.get(2).get("kind").asText()).isEqualTo("DEPOSIT");
        assertThat(bigDecimal(content.get(2), "amount")).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(content.get(3).get("kind").asText()).isEqualTo("OPENING_BALANCE");
        assertThat(bigDecimal(content.get(3), "amount")).isEqualByComparingTo(OPENING_1);
        assertThat(content.get(4).get("kind").asText()).isEqualTo("DEPOSIT");
        assertThat(bigDecimal(content.get(4), "amount")).isEqualByComparingTo(new BigDecimal("5000"));
    }

    @Test
    @DisplayName("Страница — 10 операций, вторая — остаток")
    void historyPaginatedByTen() throws Exception {
        for (int i = 0; i < 12; i++) {
            performRefill(bullionNameId1, vaultId1, 1000 + i, "2026-07-21T12:00:00", null);
        }

        JsonNode firstPage = json(history(bullionId1, null, null, null)).get("data");
        assertThat(firstPage.get("content")).hasSize(10);
        assertThat(firstPage.get("totalElements").asLong()).isEqualTo(13);
        assertThat(firstPage.get("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPage.get("pageNumber").asInt()).isEqualTo(1);

        JsonNode secondPage = json(history(bullionId1, 2, null, null)).get("data");
        assertThat(secondPage.get("content")).hasSize(3);
    }

    @Test
    @DisplayName("В истории только операции своего слитка, перевод виден с обеих сторон")
    void historyScopedToOwnBullionTransferOnBothSides() throws Exception {
        performRefill(bullionNameId2, vaultId2, 20000, "2026-07-21T12:00:00", null);
        performTransfer(bullionId1, bullionId2, 5000, "2026-07-22T12:00:00", null);

        JsonNode history1 = json(history(bullionId1, null, null, null)).get("data").get("content");
        assertThat(history1).hasSize(2);
        assertThat(history1.get(0).get("kind").asText()).isEqualTo("TRANSFER");
        assertThat(history1.get(1).get("kind").asText()).isEqualTo("OPENING_BALANCE");

        JsonNode history2 = json(history(bullionId2, null, null, null)).get("data").get("content");
        assertThat(history2).hasSize(3);
        assertThat(history2.get(0).get("kind").asText()).isEqualTo("TRANSFER");
        assertThat(history2.get(1).get("kind").asText()).isEqualTo("DEPOSIT");
        assertThat(history2.get(2).get("kind").asText()).isEqualTo("OPENING_BALANCE");
    }

    @Test
    @DisplayName("Поиск по комментарию: подстрока без учёта регистра")
    void searchByCommentCaseInsensitive() throws Exception {
        performRefill(bullionNameId1, vaultId1, 10000, "2026-07-21T12:00:00", "Зарплата за июль");
        performRefill(bullionNameId1, vaultId1, 5000, "2026-07-22T12:00:00", "Кэшбек");

        JsonNode lower = json(history(bullionId1, null, null, "зарплата")).get("data").get("content");
        assertThat(lower).hasSize(1);
        assertThat(lower.get(0).get("comment").asText()).isEqualTo("Зарплата за июль");

        JsonNode upper = json(history(bullionId1, null, null, "ПЛАТА")).get("data").get("content");
        assertThat(upper).hasSize(1);
        assertThat(upper.get(0).get("comment").asText()).isEqualTo("Зарплата за июль");
    }

    @Test
    @DisplayName("Пустой поиск не фильтрует")
    void blankSearchReturnsAll() throws Exception {
        performRefill(bullionNameId1, vaultId1, 10000, "2026-07-21T12:00:00", "Зарплата");

        JsonNode content = json(history(bullionId1, null, null, "   ")).get("data").get("content");
        assertThat(content).hasSize(2);
    }

    @Test
    @DisplayName("Поиск работает вместе с пагинацией")
    void searchWithPagination() throws Exception {
        for (int i = 1; i <= 12; i++) {
            performRefill(bullionNameId1, vaultId1, 1000 + i, "2026-07-21T12:00:00", "Аванс " + i);
        }
        performRefill(bullionNameId1, vaultId1, 500, "2026-07-22T12:00:00", "Прочее 1");
        performRefill(bullionNameId1, vaultId1, 600, "2026-07-22T13:00:00", "Прочее 2");

        JsonNode firstPage = json(history(bullionId1, null, null, "аванс")).get("data");
        assertThat(firstPage.get("totalElements").asLong()).isEqualTo(12);
        assertThat(firstPage.get("content")).hasSize(10);

        JsonNode secondPage = json(history(bullionId1, 2, null, "аванс")).get("data");
        assertThat(secondPage.get("content")).hasSize(2);
    }

    @Test
    @DisplayName("Остаток после у отправителя и получателя перевода")
    void balanceAfterForTransferBothSides() throws Exception {
        performRefill(bullionNameId1, vaultId1, 20000, "2026-07-21T12:00:00", null);
        performTransfer(bullionId1, bullionId2, 5000, "2026-07-22T12:00:00", null);

        JsonNode history1 = json(history(bullionId1, null, null, null)).get("data").get("content");
        assertThat(history1).hasSize(3);
        assertThat(bigDecimal(history1.get(0), "balanceAfter")).isEqualByComparingTo(new BigDecimal("115000"));
        assertThat(bigDecimal(history1.get(1), "balanceAfter")).isEqualByComparingTo(new BigDecimal("120000"));
        assertThat(bigDecimal(history1.get(2), "balanceAfter")).isEqualByComparingTo(OPENING_1);
        assertThat(bigDecimal(history1.get(0), "balanceAfter")).isEqualByComparingTo(bullionAmount(bullionId1));

        JsonNode history2 = json(history(bullionId2, null, null, null)).get("data").get("content");
        assertThat(history2).hasSize(2);
        assertThat(bigDecimal(history2.get(0), "balanceAfter")).isEqualByComparingTo(new BigDecimal("55000"));
        assertThat(bigDecimal(history2.get(1), "balanceAfter")).isEqualByComparingTo(OPENING_2);
    }

    @Test
    @DisplayName("Остаток после у найденной поиском операции считается по всей истории")
    void balanceAfterCorrectWithSearch() throws Exception {
        performRefill(bullionNameId1, vaultId1, 20000, "2026-07-21T12:00:00", "Крупное пополнение");
        performTransfer(bullionId1, bullionId2, 5000, "2026-07-22T12:00:00", null);

        JsonNode content = json(history(bullionId1, null, null, "крупное")).get("data").get("content");
        assertThat(content).hasSize(1);
        assertThat(bigDecimal(content.get(0), "balanceAfter")).isEqualByComparingTo(new BigDecimal("120000"));
    }

    @Test
    @DisplayName("Откат виден в истории слитка")
    void rollbackVisibleInBullionHistory() throws Exception {
        performRefill(bullionNameId1, vaultId1, 20000, "2026-07-21T12:00:00", null);
        JsonNode beforeRollback = json(history(bullionId1, null, null, null)).get("data").get("content");
        Long refillId = beforeRollback.get(0).get("id").asLong();

        mockMvc.perform(post("/api/transactions/{id}/rollback", refillId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        JsonNode content = json(history(bullionId1, null, null, null)).get("data").get("content");
        assertThat(content).hasSize(3);

        JsonNode rollbackRow = content.get(0);
        assertThat(rollbackRow.get("reversalOfId").asLong()).isEqualTo(refillId);
        assertThat(bigDecimal(rollbackRow, "balanceAfter")).isEqualByComparingTo(OPENING_1);

        JsonNode refillRow = content.get(1);
        assertThat(refillRow.get("id").asLong()).isEqualTo(refillId);
        assertThat(refillRow.get("reversedById").asLong()).isEqualTo(rollbackRow.get("id").asLong());
        assertThat(refillRow.get("canRollback").asBoolean()).isFalse();
        assertThat(bigDecimal(refillRow, "balanceAfter")).isEqualByComparingTo(new BigDecimal("120000"));
    }

    @Test
    @DisplayName("Чужой слиток — ошибка")
    void foreignBullionForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "email": "bullionhistory-other@example.com",
                            "password": "Test123%",
                            "fullName": "Other User"
                        }
                        """));
        User other = userRepository.findByEmail("bullionhistory-other@example.com").orElseThrow();
        mockMvc.perform(get("/api/auth/verify").param("token", other.getVerificationToken()));
        MvcResult otherLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "bullionhistory-other@example.com",
                                    "password": "Test123%"
                                }
                                """))
                .andReturn();
        String otherToken = json(otherLogin).get("data").get("token").asText();

        mockMvc.perform(get("/api/bullions/{bullionId}/history", bullionId1)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(BULLION_NOT_FOUND));
    }

    @Test
    @DisplayName("Несуществующий слиток — ошибка")
    void missingBullionNotFound() throws Exception {
        mockMvc.perform(get("/api/bullions/{bullionId}/history", 99999L)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(BULLION_NOT_FOUND));
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

    private Long createBullion(Long bullionNameId, Long vaultId, BigDecimal amount) throws Exception {
        String request = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": %s,
                    "dateOperation": "%s"
                }
                """.formatted(bullionNameId, vaultId, amount.toPlainString(), OPENING_DATE);

        MvcResult result = mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("data").get("id").asLong();
    }

    private void performRefill(Long bullionNameId, Long vaultId, long amount, String date, String comment) throws Exception {
        String commentJson = comment == null ? "" : ",\n\"userComment\": \"%s\"".formatted(comment);
        String request = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": %d,
                    "dateOperation": "%s"%s
                }
                """.formatted(bullionNameId, vaultId, amount, date, commentJson);

        mockMvc.perform(post("/api/bullions/refill")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
    }

    private void performWithdraw(Long bullionNameId, Long vaultId, long amount, String date, String comment) throws Exception {
        String commentJson = comment == null ? "" : ",\n\"userComment\": \"%s\"".formatted(comment);
        String request = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": %d,
                    "dateOperation": "%s"%s
                }
                """.formatted(bullionNameId, vaultId, amount, date, commentJson);

        mockMvc.perform(post("/api/bullions/withdraw")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
    }

    private void performTransfer(Long fromBullionId, Long toBullionId, long amount, String date, String comment) throws Exception {
        String commentJson = comment == null ? "" : ",\n\"comment\": \"%s\"".formatted(comment);
        String request = """
                {
                    "fromBullionId": %d,
                    "toBullionId": %d,
                    "amount": %d,
                    "dateOperation": "%s"%s
                }
                """.formatted(fromBullionId, toBullionId, amount, date, commentJson);

        mockMvc.perform(post("/api/bullions/transfer")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
    }

    private MvcResult history(Long bullionId, Integer page, Integer size, String search) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/bullions/{bullionId}/history", bullionId)
                .header("Authorization", "Bearer " + accessToken);
        if (page != null) {
            request = request.param("page", String.valueOf(page));
        }
        if (size != null) {
            request = request.param("size", String.valueOf(size));
        }
        if (search != null) {
            request = request.param("search", search);
        }
        return mockMvc.perform(request).andReturn();
    }

    private BigDecimal bullionAmount(Long bullionId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/bullions/{bullionId}", bullionId)
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn();
        return new BigDecimal(json(result).get("data").get("amount").asText());
    }

    private BigDecimal bigDecimal(JsonNode node, String field) {
        return new BigDecimal(node.get(field).asText());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
