package ru.money.goldentaurusbank.www.backend.integrationtests;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Нулевой долг стирает дату платежа и сумму к внесению")
class CreditCardPaidOffIntegrationTest extends CreditCardTestBase {

    private static final String SUM = "5000";

    private final LocalDate grace = LocalDate.now().plusDays(10);

    @Test
    @DisplayName("Полное погашение стирает дату платежа и сумму к внесению")
    void fullRepayClearsPayment() throws Exception {
        Long cardId = createCardWithSchedule("Платинум", "4321", "300000", "30000", null);

        repay(cardId, "30000").andExpect(status().isOk());

        assertPaymentCleared(cardId);
    }

    @Test
    @DisplayName("Частичное погашение дату и сумму к внесению не трогает")
    void partialRepayKeepsPayment() throws Exception {
        Long cardId = createCardWithSchedule("Платинум", "4321", "300000", "30000", null);

        repay(cardId, "10000").andExpect(status().isOk());

        assertPaymentKept(cardId);
    }

    @Test
    @DisplayName("Погашение из накопителя до нуля стирает дату и сумму к внесению")
    void repayFromBullionToZeroClearsPayment() throws Exception {
        Long vaultId = createVault("Сбер-Депозит");
        Long bullionId = createCreditBullion(vaultId, "Подушка", "50000");
        Long cardId = createCardWithSchedule("Платинум", "4321", "300000", "30000", List.of(bullionId));

        repayFromBullion(bullionId, "30000").andExpect(status().isOk());

        assertPaymentCleared(cardId);
    }

    @Test
    @DisplayName("Откат погашения возвращает долг, но не дату и сумму к внесению")
    void rollbackRepayDoesNotRestorePayment() throws Exception {
        Long cardId = createCardWithSchedule("Платинум", "4321", "300000", "30000", null);
        repay(cardId, "30000").andExpect(status().isOk());
        Long repayId = history(cardId).get(0).get("id").asLong();

        rollbackCardOperation(repayId).andExpect(status().isOk());

        assertThat(getCard(cardId).get("debt").decimalValue()).isEqualByComparingTo("30000");
        assertPaymentCleared(cardId);
    }

    @Test
    @DisplayName("Откат списания до нуля стирает дату и сумму к внесению")
    void rollbackSpendToZeroClearsPayment() throws Exception {
        Long cardId = createCard("Платинум", "4321", "300000", "0");
        spend(cardId, "20000").andExpect(status().isOk());
        Long spendId = history(cardId).get(0).get("id").asLong();
        scheduleRequest(put("/api/credit-cards/{cardId}", cardId), "Платинум", "4321", "300000", "20000",
                grace, SUM, null).andExpect(status().isOk());
        assertPaymentKept(cardId);

        rollbackCardOperation(spendId).andExpect(status().isOk());

        assertThat(getCard(cardId).get("debt").decimalValue()).isEqualByComparingTo("0");
        assertPaymentCleared(cardId);
    }

    @Test
    @DisplayName("Правка задолженности формой до нуля стирает дату и сумму к внесению")
    void formEditToZeroClearsPayment() throws Exception {
        Long cardId = createCardWithSchedule("Платинум", "4321", "300000", "30000", null);

        scheduleRequest(put("/api/credit-cards/{cardId}", cardId), "Платинум", "4321", "300000", "0",
                grace, SUM, null).andExpect(status().isOk());

        assertPaymentCleared(cardId);
    }

    @Test
    @DisplayName("Карта без долга заводится без даты и суммы к внесению")
    void cardWithoutDebtIsCreatedWithoutPayment() throws Exception {
        Long cardId = createCardWithSchedule("Платинум", "4321", "300000", "0", null);

        assertPaymentCleared(cardId);
    }

    @Test
    @DisplayName("Правка карты без долга не сохраняет дату и сумму к внесению")
    void editOfCardWithoutDebtDoesNotKeepPayment() throws Exception {
        Long cardId = createCard("Платинум", "4321", "300000", "0");

        scheduleRequest(put("/api/credit-cards/{cardId}", cardId), "Платинум", "4321", "300000", "0",
                grace, SUM, null).andExpect(status().isOk());

        assertPaymentCleared(cardId);
        assertThat(history(cardId)).isEmpty();
    }

    private void assertPaymentCleared(Long cardId) throws Exception {
        assertThat(getCard(cardId).get("gracePeriodDate").isNull()).isTrue();
        assertThat(getCard(cardId).get("paymentAmount").isNull()).isTrue();
    }

    private void assertPaymentKept(Long cardId) throws Exception {
        assertThat(getCard(cardId).get("gracePeriodDate").asText()).isEqualTo(grace.toString());
        assertThat(getCard(cardId).get("paymentAmount").decimalValue()).isEqualByComparingTo(SUM);
    }

    private Long createCardWithSchedule(String name, String last4, String limit, String debt,
                                        List<Long> bullionIds) throws Exception {
        MvcResult result = scheduleRequest(post("/api/credit-cards"), name, last4, limit, debt, grace, SUM,
                bullionIds)
                .andExpect(status().isOk())
                .andReturn();

        return dataId(result);
    }

    private ResultActions scheduleRequest(MockHttpServletRequestBuilder builder, String name, String last4,
                                          String limit, String debt, LocalDate gracePeriodDate,
                                          String paymentAmount, List<Long> bullionIds) throws Exception {
        StringBuilder body = new StringBuilder("{\"name\": \"").append(name).append('"');
        body.append(", \"last4\": \"").append(last4).append('"');
        body.append(", \"limit\": ").append(limit);
        body.append(", \"debt\": ").append(debt);
        body.append(", \"gracePeriodDate\": \"").append(gracePeriodDate).append('"');
        body.append(", \"paymentAmount\": ").append(paymentAmount);
        if (bullionIds != null) {
            body.append(", \"bullionIds\": ").append(bullionIds);
        }
        body.append('}');

        return mockMvc.perform(builder
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }
}
