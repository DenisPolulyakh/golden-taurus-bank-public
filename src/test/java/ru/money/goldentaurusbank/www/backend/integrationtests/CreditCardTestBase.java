package ru.money.goldentaurusbank.www.backend.integrationtests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Общая обвязка тестов кредитных карт: регистрация пользователя и вызовы API.
 * Своего {@code @DynamicPropertySource} здесь нет намеренно — он обязан быть
 * ровно один, в {@link IntegrationTestBase}, иначе контекст поднимется заново.
 */
abstract class CreditCardTestBase extends IntegrationTestBase {

    protected static final String EMAIL = "creditcard@example.com";
    protected static final String PASSWORD = "Test123%";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    protected String accessToken;

    @BeforeEach
    void setUpUser() throws Exception {
        accessToken = registerAndLogin(EMAIL, "Credit Card User");
    }

    protected String registerAndLogin(String email, String fullName) throws Exception {
        String registerRequest = """
                {
                    "email": "%s",
                    "password": "%s",
                    "fullName": "%s"
                }
                """.formatted(email, PASSWORD, fullName);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));

        User user = userRepository.findByEmail(email).orElseThrow();
        mockMvc.perform(get("/api/auth/verify").param("token", user.getVerificationToken()));

        String loginRequest = """
                {
                    "email": "%s",
                    "password": "%s"
                }
                """.formatted(email, PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("token").asText();
    }

    // ------------------------------------------------------------------
    // Хранилища и слитки
    // ------------------------------------------------------------------

    protected Long createVault(String name) throws Exception {
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

        return dataId(result);
    }

    protected Long createCreditBullion(Long vaultId, String title, String amount) throws Exception {
        return createBullion(vaultId, title, amount, "CREDIT");
    }

    protected Long createBullion(Long vaultId, String title, String amount, String bullionType) throws Exception {
        MvcResult bullionName = mockMvc.perform(post("/api/bullion-names")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "%s"
                                }
                                """.formatted(title)))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult result = mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "bullionNameId": %d,
                                    "vaultId": %d,
                                    "amount": %s,
                                    "bullionType": "%s"
                                }
                                """.formatted(dataId(bullionName), vaultId, amount, bullionType)))
                .andExpect(status().isOk())
                .andReturn();

        return dataId(result);
    }

    protected void refill(Long bullionId, String amount) throws Exception {
        JsonNode bullion = objectMapper.readTree(mockMvc.perform(get("/api/bullions/{bullionId}", bullionId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("data");

        mockMvc.perform(post("/api/bullions/refill")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "bullionNameId": %d,
                                    "vaultId": %d,
                                    "amount": %s
                                }
                                """.formatted(bullion.get("bullionName").get("id").asLong(),
                                bullion.get("vault").get("id").asLong(), amount)))
                .andExpect(status().isOk());
    }

    protected java.math.BigDecimal bullionAmount(Long bullionId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/bullions/{bullionId}", bullionId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("amount").decimalValue();
    }

    // ------------------------------------------------------------------
    // Карты
    // ------------------------------------------------------------------

    protected Long createCard(String name, String number, String limit, String debt) throws Exception {
        return createCard(name, number, limit, debt, null);
    }

    protected Long createCard(String name, String number, String limit, String debt, List<Long> bullionIds)
            throws Exception {
        MvcResult result = cardRequest(post("/api/credit-cards"), name, number, limit, debt, null, bullionIds)
                .andExpect(status().isOk())
                .andReturn();

        return dataId(result);
    }

    protected Long createCardWithGrace(String name, String number, String limit, LocalDate gracePeriodDate)
            throws Exception {
        MvcResult result = cardRequest(post("/api/credit-cards"), name, number, limit, "0", gracePeriodDate, null)
                .andExpect(status().isOk())
                .andReturn();

        return dataId(result);
    }

    protected void createCardExpectingError(String name, String number, String limit, String debt,
                                            List<Long> bullionIds, int code) throws Exception {
        cardRequest(post("/api/credit-cards"), name, number, limit, debt, null, bullionIds)
                .andExpect(jsonPath("$.code").value(code));
    }

    protected ResultActions updateCard(Long cardId, String name, String number, String limit, String debt,
                                       List<Long> bullionIds) throws Exception {
        return cardRequest(put("/api/credit-cards/{cardId}", cardId), name, number, limit, debt, null, bullionIds);
    }

    private ResultActions cardRequest(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                                      String name, String number, String limit, String debt,
                                      LocalDate gracePeriodDate, List<Long> bullionIds) throws Exception {
        StringBuilder body = new StringBuilder("{\"name\": \"").append(name).append('"');
        if (number != null) {
            body.append(", \"cardNumber\": \"").append(number).append('"');
        }
        if (limit != null) {
            body.append(", \"limit\": ").append(limit);
        }
        if (debt != null) {
            body.append(", \"debt\": ").append(debt);
        }
        if (gracePeriodDate != null) {
            body.append(", \"gracePeriodDate\": \"").append(gracePeriodDate).append('"');
        }
        if (bullionIds != null) {
            body.append(", \"bullionIds\": ").append(bullionIds);
        }
        body.append('}');

        return mockMvc.perform(builder
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }

    protected ResultActions deleteCard(Long cardId) throws Exception {
        return mockMvc.perform(delete("/api/credit-cards/{cardId}", cardId)
                .header("Authorization", "Bearer " + accessToken));
    }

    protected JsonNode getCard(Long cardId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/credit-cards/{cardId}", cardId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    protected JsonNode listCards(String search, String sortBy, String sortOrder) throws Exception {
        return listCardsRaw(search, sortBy, sortOrder).get("cards");
    }

    protected JsonNode listCardsRaw(String search, String sortBy, String sortOrder) throws Exception {
        var request = get("/api/credit-cards").header("Authorization", "Bearer " + accessToken);
        if (search != null) {
            request = request.param("search", search);
        }
        if (sortBy != null) {
            request = request.param("sortBy", sortBy);
        }
        if (sortOrder != null) {
            request = request.param("sortOrder", sortOrder);
        }

        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    protected JsonNode availableBullions(Long cardId) throws Exception {
        var request = get("/api/credit-cards/available-bullions")
                .header("Authorization", "Bearer " + accessToken);
        if (cardId != null) {
            request = request.param("cardId", String.valueOf(cardId));
        }

        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    protected JsonNode history(Long cardId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/credit-cards/{cardId}/history", cardId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    // ------------------------------------------------------------------
    // Операции по карте
    // ------------------------------------------------------------------

    protected ResultActions spend(Long cardId, String amount) throws Exception {
        return operation("/api/credit-cards/" + cardId + "/spend", amount);
    }

    protected ResultActions repay(Long cardId, String amount) throws Exception {
        return operation("/api/credit-cards/" + cardId + "/repay", amount);
    }

    private ResultActions operation(String url, String amount) throws Exception {
        return mockMvc.perform(post(url)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "amount": %s
                        }
                        """.formatted(amount)));
    }

    protected ResultActions repayFromBullion(Long bullionId, String amount) throws Exception {
        return mockMvc.perform(post("/api/credit-cards/repay-from-bullion")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "bullionId": %d,
                            "amount": %s
                        }
                        """.formatted(bullionId, amount)));
    }

    protected ResultActions rollbackCardOperation(Long historyId) throws Exception {
        return mockMvc.perform(post("/api/credit-cards/history/{historyId}/rollback", historyId)
                .header("Authorization", "Bearer " + accessToken));
    }

    protected JsonNode transactionHistory() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/transactions/history")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
    }

    protected ResultActions rollbackTransaction(Long transactionId) throws Exception {
        return mockMvc.perform(post("/api/transactions/{transactionId}/rollback", transactionId)
                .header("Authorization", "Bearer " + accessToken));
    }

    protected Long dataId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }
}
