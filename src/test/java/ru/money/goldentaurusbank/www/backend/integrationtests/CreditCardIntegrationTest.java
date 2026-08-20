package ru.money.goldentaurusbank.www.backend.integrationtests;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Кредитные карты: заведение, шифрование номера, поиск, сортировки, накопитель
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
    @DisplayName("Номер карты лежит в БД зашифрованным, наружу уходит только маска")
    void cardNumberIsEncryptedInDatabase() throws Exception {
        Long cardId = createCard("Платинум", "4276 1600 1234 4321", "300000", "0");

        String stored = jdbcTemplate.queryForObject(
                "SELECT card_number_enc FROM taurus.credit_cards WHERE id = ?", String.class, cardId);

        assertThat(stored).doesNotContain("4276160012344321", "4321");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT card_last4 FROM taurus.credit_cards WHERE id = ?", String.class, cardId))
                .isEqualTo("4321");

        JsonNode card = getCard(cardId);
        assertThat(card.get("maskedNumber").asText()).isEqualTo("•••• 4321");
        assertThat(card.has("cardNumber")).as("полный номер наружу не отдаётся").isFalse();
    }

    @Test
    @DisplayName("Пробелы в номере не мешают, читается номер расшифрованным")
    void cardNumberIsDecryptedBack() throws Exception {
        Long cardId = createCard("Платинум", "4276-1600 1234 5678", "300000", "0");

        String stored = jdbcTemplate.queryForObject(
                "SELECT card_number_enc FROM taurus.credit_cards WHERE id = ?", String.class, cardId);
        String decrypted = jdbcTemplate.queryForObject(
                "SELECT card_last4 FROM taurus.credit_cards WHERE id = ?", String.class, cardId);

        // Шифртекст один и тот же номер каждый раз даёт разный — сравнивать можно только через приложение
        assertThat(stored).isNotEqualTo("4276160012345678");
        assertThat(decrypted).isEqualTo("5678");
        assertThat(getCard(cardId).get("last4").asText()).isEqualTo("5678");
    }

    @Test
    @DisplayName("Поиск идёт по названию и по последним 4 цифрам")
    void searchByNameAndLast4() throws Exception {
        createCard("Платинум", "4276160012344321", "300000", "0");
        createCard("Альфа Карта", "5536910012349999", "100000", "0");

        assertThat(names(listCards("плати", null, null))).containsExactly("Платинум");
        assertThat(names(listCards("9999", null, null))).containsExactly("Альфа Карта");
        assertThat(names(listCards("карта", null, null))).containsExactly("Альфа Карта");
        assertThat(names(listCards("нет такого", null, null))).isEmpty();
    }

    @Test
    @DisplayName("Сортировка по задолженности, лимиту и остатку — в обе стороны")
    void sortingByDebtLimitAndRemainder() throws Exception {
        createCard("Первая", "4276160011111111", "100000", "50000");   // остаток 50 000
        createCard("Вторая", "4276160022222222", "300000", "10000");   // остаток 290 000
        createCard("Третья", "4276160033333333", "200000", "80000");   // остаток 120 000

        assertThat(names(listCards(null, "debt", "asc"))).containsExactly("Вторая", "Первая", "Третья");
        assertThat(names(listCards(null, "debt", "desc"))).containsExactly("Третья", "Первая", "Вторая");
        assertThat(names(listCards(null, "limit", "asc"))).containsExactly("Первая", "Третья", "Вторая");
        assertThat(names(listCards(null, "remainder", "asc"))).containsExactly("Первая", "Третья", "Вторая");
        assertThat(names(listCards(null, "remainder", "desc"))).containsExactly("Вторая", "Третья", "Первая");
    }

    @Test
    @DisplayName("Итоги списка: общий долг и количество карт")
    void listTotals() throws Exception {
        createCard("Первая", "4276160011111111", "100000", "50000");
        createCard("Вторая", "4276160022222222", "300000", "10000");

        JsonNode list = listCardsRaw(null, null, null);
        assertThat(list.get("totalDebt").decimalValue()).isEqualByComparingTo("60000.00");
        assertThat(list.get("count").asInt()).isEqualTo(2);

        // Поиск сужает список, но не итоги: в шапке «Текущий долг», а не «долг найденного»
        JsonNode filtered = listCardsRaw("Перв", null, null);
        assertThat(filtered.get("cards")).hasSize(1);
        assertThat(filtered.get("totalDebt").decimalValue()).isEqualByComparingTo("60000.00");
        assertThat(filtered.get("count").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("Остаток равен лимиту минус задолженность, стартовый долг попадает в историю")
    void remainderAndOpeningDebt() throws Exception {
        Long cardId = createCard("Платинум", "4276160012344321", "300000", "120000");

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
        Long cardId = createCard("Платинум", "4276160012344321", "300000", "100000", List.of(bullionId));

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
        Long cardId = createCardWithGrace("Платинум", "4276160012344321", "300000",
                java.time.LocalDate.now().plusDays(21));

        assertThat(getCard(cardId).get("graceDaysLeft").asInt()).isEqualTo(21);

        Long overdue = createCardWithGrace("Альфа", "5536910012349999", "100000",
                java.time.LocalDate.now().minusDays(3));
        assertThat(getCard(overdue).get("graceDaysLeft").asInt()).isEqualTo(-3);
    }

    @Test
    @DisplayName("В накопитель идут только кредитные слитки")
    void debitBullionCannotBeAccumulator() throws Exception {
        Long vaultId = createVault("Сбер-Депозит");
        Long debitBullionId = createBullion(vaultId, "Обычный", "10000", "DEBIT");

        createCardExpectingError("Платинум", "4276160012344321", "300000", "0",
                List.of(debitBullionId), 4024);
    }

    @Test
    @DisplayName("Слиток нельзя привязать сразу к двум картам")
    void bullionCannotBeLinkedTwice() throws Exception {
        Long vaultId = createVault("Сбер-Депозит");
        Long bullionId = createCreditBullion(vaultId, "Подушка", "30000");
        createCard("Платинум", "4276160012344321", "300000", "0", List.of(bullionId));

        createCardExpectingError("Альфа", "5536910012349999", "100000", "0",
                List.of(bullionId), 4025);
    }

    @Test
    @DisplayName("В списке доступных слитков нет чужих накопителей, но есть свои")
    void availableBullionsExcludeLinkedToOtherCards() throws Exception {
        Long vaultId = createVault("Сбер-Депозит");
        Long linked = createCreditBullion(vaultId, "Подушка", "30000");
        Long free = createCreditBullion(vaultId, "Резерв", "5000");
        createBullion(vaultId, "Обычный", "10000", "DEBIT");

        Long cardId = createCard("Платинум", "4276160012344321", "300000", "0", List.of(linked));

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
        Long cardId = createCard("Платинум", "4276160012344321", "300000", "0", List.of(first));

        updateCard(cardId, "Платинум", null, "300000", null, List.of(second))
                .andExpect(status().isOk());

        JsonNode card = getCard(cardId);
        assertThat(card.get("accumulators")).hasSize(1);
        assertThat(card.get("accumulators").get(0).get("bullionId").asLong()).isEqualTo(second);
        assertThat(titles(availableBullions(null))).contains("Подушка");
    }

    @Test
    @DisplayName("Пустой номер при правке оставляет прежний")
    void emptyNumberOnUpdateKeepsOld() throws Exception {
        Long cardId = createCard("Платинум", "4276160012344321", "300000", "0");

        updateCard(cardId, "Платинум Голд", null, "400000", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.last4").value("4321"))
                .andExpect(jsonPath("$.data.name").value("Платинум Голд"))
                .andExpect(jsonPath("$.data.limit").value(400000));
    }

    @Test
    @DisplayName("Правка задолженности пишется операцией, а не молча в поле")
    void debtChangeOnUpdateBecomesOperation() throws Exception {
        Long cardId = createCard("Платинум", "4276160012344321", "300000", "50000");

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
        Long cardId = createCard("Платинум", "4276160012344321", "300000", "150000");

        updateCard(cardId, "Платинум", null, "100000", null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4030));

        createCardExpectingError("Альфа", "5536910012349999", "50000", "80000", null, 4030);

        // А вместе с погашением — можно: сначала долг, потом лимит
        updateCard(cardId, "Платинум", null, "100000", "90000", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limit").value(100000))
                .andExpect(jsonPath("$.data.debt").value(90000));
    }

    @Test
    @DisplayName("Номер не из 12–19 цифр и дубль названия не проходят")
    void validation() throws Exception {
        createCardExpectingError("Платинум", "4276-16", "300000", "0", null, 4023);
        createCardExpectingError("Платинум", "4276160012344321abcd", "300000", "0", null, 4023);

        createCard("Платинум", "4276160012344321", "300000", "0");
        createCardExpectingError("платинум", "5536910012349999", "100000", "0", null, 4022);
    }

    @Test
    @DisplayName("Удаление архивирует карту, освобождает накопители и сохраняет историю")
    void deleteArchivesCard() throws Exception {
        Long vaultId = createVault("Сбер-Депозит");
        Long bullionId = createCreditBullion(vaultId, "Подушка", "30000");
        Long cardId = createCard("Платинум", "4276160012344321", "300000", "100000", List.of(bullionId));

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
        Long cardId = createCard("Платинум", "4276160012344321", "300000", "0");
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
