package ru.money.goldentaurusbank.www.backend.integrationtests;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Операции по кредитной карте: списание лимита, погашение, погашение из
 * накопителя и откат (см. plans/PLAN_CREDIT_CARD.md).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Интеграционные тесты операций по кредитным картам")
class CreditCardTransactionIntegrationTest extends CreditCardTestBase {

    @Test
    @DisplayName("Списание увеличивает задолженность и уменьшает остаток")
    void spendIncreasesDebt() throws Exception {
        Long cardId = createCard("Платинум", "4321", "300000", "0");

        spend(cardId, "120000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.debt").value(120000))
                .andExpect(jsonPath("$.data.remainder").value(180000));

        JsonNode history = history(cardId);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("operation").asText()).isEqualTo("SPEND");
        assertThat(history.get(0).get("signedAmount").decimalValue()).isEqualByComparingTo("120000.00");
        assertThat(history.get(0).get("canRollback").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Больше лимита списать нельзя, задолженность при этом не двигается")
    void spendOverLimitIsRejected() throws Exception {
        Long cardId = createCard("Платинум", "4321", "100000", "60000");

        spend(cardId, "50000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4020));

        assertThat(getCard(cardId).get("debt").decimalValue()).isEqualByComparingTo("60000.00");
        // Неуспешная операция в историю не попадает — там только стартовый долг
        assertThat(history(cardId)).hasSize(1);
    }

    @Test
    @DisplayName("Погашение уменьшает задолженность, больше долга погасить нельзя")
    void repayDecreasesDebt() throws Exception {
        Long cardId = createCard("Платинум", "4321", "300000", "100000");

        repay(cardId, "40000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.debt").value(60000))
                .andExpect(jsonPath("$.data.remainder").value(240000));

        repay(cardId, "60000.01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4021));

        assertThat(getCard(cardId).get("debt").decimalValue()).isEqualByComparingTo("60000.00");
    }

    @Test
    @DisplayName("Откат списания и погашения возвращает задолженность, дважды не откатывается")
    void rollbackReturnsDebt() throws Exception {
        Long cardId = createCard("Платинум", "4321", "300000", "0");
        spend(cardId, "50000").andExpect(status().isOk());

        Long spendId = history(cardId).get(0).get("id").asLong();
        rollbackCardOperation(spendId).andExpect(status().isOk());

        assertThat(getCard(cardId).get("debt").decimalValue()).isEqualByComparingTo("0.00");

        rollbackCardOperation(spendId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4027));

        // Откат — обратная запись, а не удаление: в истории обе операции
        JsonNode history = history(cardId);
        assertThat(history).hasSize(2);
        JsonNode reversal = history.get(0);
        assertThat(reversal.get("operation").asText()).isEqualTo("REPAY");
        assertThat(reversal.get("reversalOfId").asLong()).isEqualTo(spendId);
        assertThat(reversal.get("canRollback").asBoolean()).as("откат откату не подлежит").isFalse();

        // И обратно: погашение тоже откатывается
        spend(cardId, "30000").andExpect(status().isOk());
        repay(cardId, "10000").andExpect(status().isOk());
        Long repayId = history(cardId).get(0).get("id").asLong();
        rollbackCardOperation(repayId).andExpect(status().isOk());

        assertThat(getCard(cardId).get("debt").decimalValue()).isEqualByComparingTo("30000.00");
    }

    @Test
    @DisplayName("Погашение из накопителя списывает со слитка и гасит долг")
    void repayFromBullionMovesBothSides() throws Exception {
        Long vaultId = createVault("Сбер-Депозит");
        Long bullionId = createCreditBullion(vaultId, "Подушка", "80000");
        Long cardId = createCard("Платинум", "4321", "300000", "100000", List.of(bullionId));

        repayFromBullion(bullionId, "50000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.debt").value(50000));

        assertThat(bullionAmount(bullionId)).isEqualByComparingTo("30000.00");
        assertThat(getCard(cardId).get("imbalance").decimalValue()).isEqualByComparingTo("-20000.00");

        JsonNode operation = history(cardId).get(0);
        assertThat(operation.get("operation").asText()).isEqualTo("REPAY");
        assertThat(operation.get("bullionTransactionId").isNull()).isFalse();
    }

    @Test
    @DisplayName("Откат погашения из накопителя возвращает и слиток, и долг")
    void rollbackRepayFromBullionReturnsBothSides() throws Exception {
        Long vaultId = createVault("Сбер-Депозит");
        Long bullionId = createCreditBullion(vaultId, "Подушка", "80000");
        Long cardId = createCard("Платинум", "4321", "300000", "100000", List.of(bullionId));

        repayFromBullion(bullionId, "50000").andExpect(status().isOk());
        Long operationId = history(cardId).get(0).get("id").asLong();

        rollbackCardOperation(operationId).andExpect(status().isOk());

        assertThat(bullionAmount(bullionId)).isEqualByComparingTo("80000.00");
        assertThat(getCard(cardId).get("debt").decimalValue()).isEqualByComparingTo("100000.00");
    }

    @Test
    @DisplayName("Ногу погашения нельзя откатить из общей истории операций")
    void bullionLegCannotBeRolledBackAlone() throws Exception {
        Long vaultId = createVault("Сбер-Депозит");
        Long bullionId = createCreditBullion(vaultId, "Подушка", "80000");
        createCard("Платинум", "4321", "300000", "100000", List.of(bullionId));

        repayFromBullion(bullionId, "50000").andExpect(status().isOk());

        JsonNode withdrawal = null;
        for (JsonNode transaction : transactionHistory()) {
            if ("WITHDRAWAL".equals(transaction.get("kind").asText())) {
                withdrawal = transaction;
            }
        }
        assertThat(withdrawal).as("списание со слитка должно быть в истории").isNotNull();
        assertThat(withdrawal.get("canRollback").asBoolean()).isFalse();
        assertThat(withdrawal.get("lockedByCard").asBoolean()).isTrue();

        rollbackTransaction(withdrawal.get("id").asLong())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4029));

        assertThat(bullionAmount(bullionId)).isEqualByComparingTo("30000.00");
    }

    @Test
    @DisplayName("Погасить можно только со слитка, привязанного к карте")
    void repayFromUnlinkedBullionIsRejected() throws Exception {
        Long vaultId = createVault("Сбер-Депозит");
        Long freeBullionId = createCreditBullion(vaultId, "Резерв", "80000");
        createCard("Платинум", "4321", "300000", "100000");

        repayFromBullion(freeBullionId, "10000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4028));

        assertThat(bullionAmount(freeBullionId)).isEqualByComparingTo("80000.00");
    }

    @Test
    @DisplayName("Погашение больше долга не проходит и слиток не трогает")
    void repayFromBullionOverDebtKeepsBullionIntact() throws Exception {
        Long vaultId = createVault("Сбер-Депозит");
        Long bullionId = createCreditBullion(vaultId, "Подушка", "80000");
        Long cardId = createCard("Платинум", "4321", "300000", "20000", List.of(bullionId));

        repayFromBullion(bullionId, "50000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4021));

        assertThat(bullionAmount(bullionId)).isEqualByComparingTo("80000.00");
        assertThat(getCard(cardId).get("debt").decimalValue()).isEqualByComparingTo("20000.00");
    }

    @Test
    @DisplayName("Из хранилища без «Можно снимать» погасить нельзя")
    void repayFromBullionRespectsVaultFlags() throws Exception {
        Long vaultId = createVault("Сбер-Депозит");
        Long bullionId = createCreditBullion(vaultId, "Подушка", "80000");
        Long cardId = createCard("Платинум", "4321", "300000", "100000", List.of(bullionId));

        forbidExpense(vaultId, "Сбер-Депозит");

        repayFromBullion(bullionId, "10000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4016));

        assertThat(bullionAmount(bullionId)).isEqualByComparingTo("80000.00");
        assertThat(getCard(cardId).get("debt").decimalValue()).isEqualByComparingTo("100000.00");
    }

    @Test
    @DisplayName("Чужой картой распоряжаться нельзя")
    void otherUserCannotOperateCard() throws Exception {
        Long cardId = createCard("Платинум", "4321", "300000", "100000");

        accessToken = registerAndLogin("othercardops@example.com", "Other Card Ops");

        spend(cardId, "1000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4019));
        repay(cardId, "1000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4019));
    }

    @Test
    @DisplayName("Задолженность попадает в статистику графика")
    void debtGoesToDashboardStatistics() throws Exception {
        Long cardId = createCard("Платинум", "4321", "300000", "0");
        spend(cardId, "70000").andExpect(status().isOk());

        java.time.LocalDate today = java.time.LocalDate.now();
        JsonNode daily = objectMapper.readTree(mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/transactions/dashboard/daily-statistics")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("year", String.valueOf(today.getYear()))
                                .param("month", String.valueOf(today.getMonthValue())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(daily.get("totalDebt").decimalValue()).isEqualByComparingTo("70000.00");

        JsonNode todayPoint = daily.get("dailyData").get(today.getDayOfMonth() - 1);
        assertThat(todayPoint.get("debt").decimalValue()).isEqualByComparingTo("70000.00");

        // До сегодняшнего дня долга ещё не было
        if (today.getDayOfMonth() > 1) {
            assertThat(daily.get("dailyData").get(0).get("debt").decimalValue()).isEqualByComparingTo("0.00");
        }
    }

    private void forbidExpense(Long vaultId, String name) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/vaults/{vaultId}", vaultId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "%s",
                                    "allowedExpense": false
                                }
                                """.formatted(name)))
                .andExpect(status().isOk());
    }
}
