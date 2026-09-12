package ru.money.goldentaurusbank.www.backend.integrationtests;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Кредитные карты: заведение, последние 4 цифры, поиск, сортировки, накопитель
 * и архивация (см. plans/PLAN_CREDIT_CARD.md).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Интеграционные тесты кредитных карт")
class CreditCardIntegrationTest extends CreditCardTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("От номера хранятся только последние 4 цифры, колонки под полный номер нет")
    void onlyLast4IsStored() throws Exception {
        Long cardId = createCard("Платинум", "4321", "300000", "0");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT card_last4 FROM taurus.credit_cards WHERE id = ?", String.class, cardId))
                .isEqualTo("4321");

        // Колонку под шифртекст снесла миграция 012: полный номер хранить негде
        Integer columns = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'taurus' AND table_name = 'credit_cards' "
                        + "AND column_name = 'card_number_enc'", Integer.class);
        assertThat(columns).isZero();

        JsonNode card = getCard(cardId);
        assertThat(card.get("maskedNumber").asText()).isEqualTo("•••• 4321");
        assertThat(card.get("last4").asText()).isEqualTo("4321");
        assertThat(card.has("cardNumber")).as("полного номера нет ни в базе, ни в ответе").isFalse();
    }

    @Test
    @DisplayName("Пробелы и дефисы вокруг цифр не мешают")
    void last4IsNormalized() throws Exception {
        Long cardId = createCard("Платинум", " 5678 ", "300000", "0");

        assertThat(getCard(cardId).get("last4").asText()).isEqualTo("5678");
    }

    @Test
    @DisplayName("Поиск идёт по названию и по последним 4 цифрам")
    void searchByNameAndLast4() throws Exception {
        createCard("Платинум", "4321", "300000", "0");
        createCard("Альфа Карта", "9999", "100000", "0");

        assertThat(names(listCards("плати", null, null))).containsExactly("Платинум");
        assertThat(names(listCards("9999", null, null))).containsExactly("Альфа Карта");
        assertThat(names(listCards("карта", null, null))).containsExactly("Альфа Карта");
        assertThat(names(listCards("нет такого", null, null))).isEmpty();
    }

    @Test
    @DisplayName("Сортировка по задолженности, лимиту и остатку — в обе стороны")
    void sortingByDebtLimitAndRemainder() throws Exception {
        createCard("Первая", "1111", "100000", "50000");   // остаток 50 000
        createCard("Вторая", "2222", "300000", "10000");   // остаток 290 000
        createCard("Третья", "3333", "200000", "80000");   // остаток 120 000

        assertThat(names(listCards(null, "debt", "asc"))).containsExactly("Вторая", "Первая", "Третья");
        assertThat(names(listCards(null, "debt", "desc"))).containsExactly("Третья", "Первая", "Вторая");
        assertThat(names(listCards(null, "limit", "asc"))).containsExactly("Первая", "Третья", "Вторая");
        assertThat(names(listCards(null, "remainder", "asc"))).containsExactly("Первая", "Третья", "Вторая");
        assertThat(names(listCards(null, "remainder", "desc"))).containsExactly("Вторая", "Третья", "Первая");
    }

    @Test
    @DisplayName("Итоги списка: общий долг, общий лимит и количество карт")
    void listTotals() throws Exception {
        createCard("Первая", "1111", "100000", "50000");
        Long second = createCard("Вторая", "2222", "300000", "10000");

        JsonNode list = listCardsRaw(null, null, null);
        assertThat(list.get("totalDebt").decimalValue()).isEqualByComparingTo("60000.00");
        assertThat(list.get("totalLimit").decimalValue()).isEqualByComparingTo("400000.00");
        assertThat(list.get("count").asInt()).isEqualTo(2);

        // Поиск сужает список, но не итоги: в шапке «Текущий долг», а не «долг найденного»
        JsonNode filtered = listCardsRaw("Перв", null, null);
        assertThat(filtered.get("cards")).hasSize(1);
        assertThat(filtered.get("totalDebt").decimalValue()).isEqualByComparingTo("60000.00");
        assertThat(filtered.get("totalLimit").decimalValue()).isEqualByComparingTo("400000.00");
        assertThat(filtered.get("count").asInt()).isEqualTo(2);

        // Архивная карта из итогов уходит вместе со своим лимитом
        deleteCard(second).andExpect(status().isOk());
        JsonNode afterDelete = listCardsRaw(null, null, null);
        assertThat(afterDelete.get("totalLimit").decimalValue()).isEqualByComparingTo("100000.00");
    }

    @Test
    @DisplayName("Остаток равен лимиту минус задолженность, стартовый долг попадает в историю")
    void remainderAndOpeningDebt() throws Exception {
        Long cardId = createCard("Платинум", "4321", "300000", "120000");

        JsonNode card = getCard(cardId);
        assertThat(card.get("debt").decimalValue()).isEqualByComparingTo("120000.00");
        assertThat(card.get("remainder").decimalValue()).isEqualByComparingTo("180000.00");

        JsonNode history = history(cardId);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("operation").asText()).isEqualTo("OPENING_DEBT");
        assertThat(history.get(0).get("debtAfter").decimalValue()).isEqualByComparingTo("120000.00");
    }

    @Test
    @DisplayName("Дисбаланс — накопитель минус долг: не хватает, значит минус")
    void imbalanceIsAccumulatedMinusDebt() throws Exception {
        Long vaultId = createVault("Сбер-Депозит");
        Long bullionId = createCreditBullion(vaultId, "Подушка", "30000");
        Long cardId = createCard("Платинум", "4321", "300000", "100000", List.of(bullionId));

        JsonNode card = getCard(cardId);
        assertThat(card.get("accumulatedAmount").decimalValue()).isEqualByComparingTo("30000.00");
        assertThat(card.get("imbalance").decimalValue()).isEqualByComparingTo("-70000.00");
        assertThat(card.get("accumulators")).hasSize(1);
        assertThat(card.get("accumulators").get(0).get("vaultName").asText()).isEqualTo("Сбер-Депозит");

        // Накопили больше долга — дисбаланс становится плюсовым
        refill(bullionId, "150000");
        assertThat(getCard(cardId).get("imbalance").decimalValue()).isEqualByComparingTo("80000.00");
    }

    @Test
    @DisplayName("Льготный период отдаётся счётчиком дней")
    void graceDaysLeftIsCounted() throws Exception {
        Long cardId = createCardWithGrace("Платинум", "4321", "300000",
                java.time.LocalDate.now().plusDays(21));

        assertThat(getCard(cardId).get("graceDaysLeft").asInt()).isEqualTo(21);

        Long overdue = createCardWithGrace("Альфа", "9999", "100000",
                java.time.LocalDate.now().minusDays(3));
        assertThat(getCard(overdue).get("graceDaysLeft").asInt()).isEqualTo(-3);
    }

    @Test
    @DisplayName("Сортировка по остатку дней: сначала ближайший срок, карты без периода — в конце")
    void sortingByGraceDaysLeft() throws Exception {
        createCardWithGrace("Через месяц", "1111", "100000", java.time.LocalDate.now().plusDays(30));
        createCardWithGrace("Послезавтра", "2222", "100000", java.time.LocalDate.now().plusDays(2));
        createCardWithGrace("Просрочена", "3333", "100000", java.time.LocalDate.now().minusDays(5));
        createCard("Без периода", "4444", "100000", "0");

        // Пустой sortBy — та же сортировка: это и есть порядок по умолчанию
        assertThat(names(listCards(null, null, null)))
                .containsExactly("Просрочена", "Послезавтра", "Через месяц", "Без периода");
        assertThat(names(listCards(null, "grace", "asc")))
                .containsExactly("Просрочена", "Послезавтра", "Через месяц", "Без периода");

        // Переворот меняет порядок дат, но карту без периода наверх не поднимает:
        // «не задан» — это не «дней много»
        assertThat(names(listCards(null, "grace", "desc")))
                .containsExactly("Через месяц", "Послезавтра", "Просрочена", "Без периода");
    }

    @Test
    @DisplayName("В накопитель идут только кредитные слитки")
    void debitBullionCannotBeAccumulator() throws Exception {
        Long vaultId = createVault("Сбер-Депозит");
        Long debitBullionId = createBullion(vaultId, "Обычный", "10000", "DEBIT");

        createCardExpectingError("Платинум", "4321", "300000", "0",
                List.of(debitBullionId), 4024);
    }

    @Test
    @DisplayName("Сводка «Мои слитки» знает про накопитель: карта, маска и её долг")
    void groupedBullionsCarryCreditCard() throws Exception {
        Long vaultId = createVault("Сбер-Депозит");
        Long bullionId = createCreditBullion(vaultId, "Подушка", "30000");
        Long cardId = createCard("Платинум", "4321", "300000", "100000", List.of(bullionId));

        JsonNode vault = groupedVaults("Подушка").get(0);
        assertThat(vault.get("creditCardId").asLong()).isEqualTo(cardId);
        assertThat(vault.get("creditCardMasked").asText()).isEqualTo("•••• 4321");
        assertThat(vault.get("creditCardDebt").decimalValue()).isEqualByComparingTo("100000.00");
    }

    @Test
    @DisplayName("Слиток без карты в сводке приходит с пустым накопителем")
    void groupedBullionWithoutCardHasNoAccumulator() throws Exception {
        Long vaultId = createVault("Сбер-Депозит");
        createBullion(vaultId, "Обычный", "10000", "DEBIT");

        JsonNode vault = groupedVaults("Обычный").get(0);
        assertThat(vault.get("creditCardId").isNull()).isTrue();
    }

    @Test
    @DisplayName("Слиток нельзя привязать сразу к двум картам")
    void bullionCannotBeLinkedTwice() throws Exception {
        Long vaultId = createVault("Сбер-Депозит");
        Long bullionId = createCreditBullion(vaultId, "Подушка", "30000");
        createCard("Платинум", "4321", "300000", "0", List.of(bullionId));

        createCardExpectingError("Альфа", "9999", "100000", "0",
                List.of(bullionId), 4025);
    }

    @Test
    @DisplayName("В списке доступных слитков нет чужих накопителей, но есть свои")
    void availableBullionsExcludeLinkedToOtherCards() throws Exception {
        Long vaultId = createVault("Сбер-Депозит");
        Long linked = createCreditBullion(vaultId, "Подушка", "30000");
        Long free = createCreditBullion(vaultId, "Резерв", "5000");
        createBullion(vaultId, "Обычный", "10000", "DEBIT");

        Long cardId = createCard("Платинум", "4321", "300000", "0", List.of(linked));

        // Без карты — только свободные кредитные
        List<String> anyCard = titles(availableBullions(null));
        assertThat(anyCard).containsExactly("Резерв");

        // С картой — плюс её собственные накопители, иначе при правке они исчезнут
        List<String> forCard = titles(availableBullions(cardId));
        assertThat(forCard).containsExactlyInAnyOrder("Подушка", "Резерв");
        assertThat(free).isNotNull();
    }

    @Test
    @DisplayName("Правка карты меняет состав накопителя и отвязывает выбывшие слитки")
    void updateReplacesAccumulators() throws Exception {
        Long vaultId = createVault("Сбер-Депозит");
        Long first = createCreditBullion(vaultId, "Подушка", "30000");
        Long second = createCreditBullion(vaultId, "Резерв", "5000");
        Long cardId = createCard("Платинум", "4321", "300000", "0", List.of(first));

        updateCard(cardId, "Платинум", null, "300000", null, List.of(second))
                .andExpect(status().isOk());

        JsonNode card = getCard(cardId);
        assertThat(card.get("accumulators")).hasSize(1);
        assertThat(card.get("accumulators").get(0).get("bullionId").asLong()).isEqualTo(second);
        assertThat(titles(availableBullions(null))).contains("Подушка");
    }

    @Test
    @DisplayName("Пустое поле при правке оставляет прежние 4 цифры")
    void emptyLast4OnUpdateKeepsOld() throws Exception {
        Long cardId = createCard("Платинум", "4321", "300000", "0");

        updateCard(cardId, "Платинум Голд", null, "400000", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.last4").value("4321"))
                .andExpect(jsonPath("$.data.name").value("Платинум Голд"))
                .andExpect(jsonPath("$.data.limit").value(400000));
    }

    @Test
    @DisplayName("Правка задолженности пишется операцией, а не молча в поле")
    void debtChangeOnUpdateBecomesOperation() throws Exception {
        Long cardId = createCard("Платинум", "4321", "300000", "50000");

        updateCard(cardId, "Платинум", null, "300000", "80000", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.debt").value(80000));

        JsonNode history = history(cardId);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).get("operation").asText()).isEqualTo("SPEND");
        assertThat(history.get(0).get("amount").decimalValue()).isEqualByComparingTo("30000.00");
    }

    @Test
    @DisplayName("Лимит ниже задолженности не сохраняется")
    void limitBelowDebtIsRejected() throws Exception {
        Long cardId = createCard("Платинум", "4321", "300000", "150000");

        updateCard(cardId, "Платинум", null, "100000", null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4030));

        createCardExpectingError("Альфа", "9999", "50000", "80000", null, 4030);

        // А вместе с погашением — можно: сначала долг, потом лимит
        updateCard(cardId, "Платинум", null, "100000", "90000", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limit").value(100000))
                .andExpect(jsonPath("$.data.debt").value(90000));
    }

    @Test
    @DisplayName("Не ровно 4 цифры и дубль названия не проходят")
    void validation() throws Exception {
        createCardExpectingError("Платинум", "432", "300000", "0", null, 4023);
        createCardExpectingError("Платинум", "43210", "300000", "0", null, 4023);
        // Полный номер тоже отлуп: хранить его негде, а обрезать молча — обманывать
        createCardExpectingError("Платинум", "4276160012344321", "300000", "0", null, 4023);
        createCardExpectingError("Платинум", "abcd", "300000", "0", null, 4023);

        createCard("Платинум", "4321", "300000", "0");
        createCardExpectingError("платинум", "9999", "100000", "0", null, 4022);
    }

    @Test
    @DisplayName("Карта заводится с суммой к внесению, она приходит в ответе")
    void paymentAmountIsSaved() throws Exception {
        Long cardId = createCardWithPayment("Платинум", "4321", "300000", "20000");

        assertThat(getCard(cardId).get("paymentAmount").decimalValue()).isEqualByComparingTo("20000");
    }

    @Test
    @DisplayName("Сумма к внесению необязательна: без неё карта заводится с пустым полем")
    void paymentAmountIsOptional() throws Exception {
        Long cardId = createCard("Без платежа", "1111", "100000", "0");

        assertThat(getCard(cardId).get("paymentAmount").isNull()).isTrue();
    }

    @Test
    @DisplayName("Отрицательная сумма к внесению не принимается")
    void negativePaymentAmountRejected() throws Exception {
        paymentRequest(post("/api/credit-cards"), "Минус", "2222", "100000", "0", "-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    @DisplayName("Правка меняет сумму к внесению, пустое значение её очищает")
    void paymentAmountIsEditable() throws Exception {
        Long cardId = createCardWithPayment("Платинум", "4321", "300000", "20000");

        paymentRequest(put("/api/credit-cards/{cardId}", cardId), "Платинум", null, "300000", "0", "35000")
                .andExpect(status().isOk());
        assertThat(getCard(cardId).get("paymentAmount").decimalValue()).isEqualByComparingTo("35000");

        paymentRequest(put("/api/credit-cards/{cardId}", cardId), "Платинум", null, "300000", "0", null)
                .andExpect(status().isOk());
        assertThat(getCard(cardId).get("paymentAmount").isNull()).isTrue();
    }

    @Test
    @DisplayName("Сумма к внесению не участвует в проверке «долг не больше лимита»")
    void paymentAmountDoesNotAffectLimit() throws Exception {
        Long cardId = createCardWithPayment("Платинум", "4321", "100000", "100000", "999999");

        JsonNode card = getCard(cardId);

        assertThat(card.get("remainder").decimalValue()).isEqualByComparingTo("0");
        assertThat(card.get("paymentAmount").decimalValue()).isEqualByComparingTo("999999");
    }

    private Long createCardWithPayment(String name, String last4, String limit, String paymentAmount)
            throws Exception {
        return createCardWithPayment(name, last4, limit, "0", paymentAmount);
    }

    private Long createCardWithPayment(String name, String last4, String limit, String debt, String paymentAmount)
            throws Exception {
        MvcResult result = paymentRequest(post("/api/credit-cards"), name, last4, limit, debt, paymentAmount)
                .andExpect(status().isOk())
                .andReturn();

        return dataId(result);
    }

    private ResultActions paymentRequest(MockHttpServletRequestBuilder builder, String name, String last4,
                                         String limit, String debt, String paymentAmount) throws Exception {
        StringBuilder body = new StringBuilder("{\"name\": \"").append(name).append('"');
        if (last4 != null) {
            body.append(", \"last4\": \"").append(last4).append('"');
        }
        body.append(", \"limit\": ").append(limit);
        body.append(", \"debt\": ").append(debt);
        body.append(", \"paymentAmount\": ").append(paymentAmount == null ? "null" : paymentAmount);
        body.append('}');

        return mockMvc.perform(builder
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }

    @Test
    @DisplayName("Удаление архивирует карту, освобождает накопители и сохраняет историю")
    void deleteArchivesCard() throws Exception {
        Long vaultId = createVault("Сбер-Депозит");
        Long bullionId = createCreditBullion(vaultId, "Подушка", "30000");
        Long cardId = createCard("Платинум", "4321", "300000", "100000", List.of(bullionId));

        deleteCard(cardId).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT archived FROM taurus.credit_cards WHERE id = ?", Boolean.class, cardId)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT credit_card_id FROM taurus.bullions WHERE id = ?", Long.class, bullionId)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM taurus.credit_card_history WHERE credit_card_id = ?", Integer.class, cardId))
                .isEqualTo(1);

        JsonNode list = listCardsRaw(null, null, null);
        assertThat(list.get("cards")).isEmpty();
        assertThat(list.get("totalDebt").decimalValue()).isEqualByComparingTo("0.00");

        // Слиток освободился и снова доступен для другой карты
        assertThat(titles(availableBullions(null))).containsExactly("Подушка");
    }

    @Test
    @DisplayName("Чужая карта не читается и не правится")
    void otherUsersCardIsInvisible() throws Exception {
        Long cardId = createCard("Платинум", "4321", "300000", "0");
        String otherToken = registerAndLogin("othercard@example.com", "Other Card User");

        mockMvc.perform(get("/api/credit-cards/{cardId}", cardId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4019));

        mockMvc.perform(get("/api/credit-cards")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));
    }

    // Вспомогательные методы

    private List<String> names(JsonNode cards) {
        List<String> result = new ArrayList<>();
        cards.forEach(card -> result.add(card.get("name").asText()));
        return result;
    }

    private List<String> titles(JsonNode bullions) {
        List<String> result = new ArrayList<>();
        bullions.forEach(bullion -> result.add(bullion.get("bullionNameTitle").asText()));
        return result;
    }
}
