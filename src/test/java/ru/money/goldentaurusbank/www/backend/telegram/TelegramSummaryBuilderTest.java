package ru.money.goldentaurusbank.www.backend.telegram;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.money.goldentaurusbank.www.backend.model.dto.response.CreditCardListResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.CreditCardResponse;
import ru.money.goldentaurusbank.www.backend.service.telegram.TelegramSummaryBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Сводка по картам для телеграма")
class TelegramSummaryBuilderTest {

    private final TelegramSummaryBuilder builder = new TelegramSummaryBuilder();

    @Test
    @DisplayName("Карт нет — сводка это прямо говорит")
    void noCards() {
        String text = builder.build(CreditCardListResponse.builder()
                .cards(List.of())
                .totalDebt(BigDecimal.ZERO)
                .totalLimit(BigDecimal.ZERO)
                .build());

        assertThat(text).isEqualTo("Карт пока нет");
    }

    @Test
    @DisplayName("Есть сумма к внесению — показывается она, иначе долг")
    void paymentAmountBeatsDebt() {
        CreditCardResponse withPayment = card("Платинум", "4567", "200000", "20000", 7);
        CreditCardResponse withoutPayment = card("Альфа", "1234", "120000", null, 12);

        String text = builder.build(list(List.of(withPayment, withoutPayment), "320000", "500000"));

        assertThat(text).contains("Платинум ****4567 — 20 000 ₽");
        assertThat(text).contains("Альфа ****1234 — долг 120 000 ₽");
    }

    @Test
    @DisplayName("Карты без долга в блок платежей не попадают")
    void zeroDebtCardsAreSkipped() {
        CreditCardResponse paid = card("Пустая", "1111", "0", null, 5);
        CreditCardResponse owing = card("Платинум", "4567", "200000", null, 7);

        String text = builder.build(list(List.of(paid, owing), "200000", "500000"));

        assertThat(text).doesNotContain("Пустая");
        assertThat(text).contains("Платинум");
    }

    @Test
    @DisplayName("Недостача накопителей суммируется по всем картам")
    void shortageIsSummed() {
        CreditCardResponse first = card("Платинум", "4567", "200000", "20000", 7);
        first.setAccumulatedAmount(new BigDecimal("5000"));
        CreditCardResponse second = card("Альфа", "1234", "100000", null, 12);
        second.setAccumulatedAmount(new BigDecimal("70000"));

        String text = builder.build(list(List.of(first, second), "300000", "500000"));

        assertThat(text).contains("не хватает 45 000 ₽");
    }

    @Test
    @DisplayName("Накопленного достаточно — сводка сообщает, что хватает")
    void enoughAccumulated() {
        CreditCardResponse card = card("Платинум", "4567", "200000", "20000", 7);
        card.setAccumulatedAmount(new BigDecimal("25000"));

        String text = builder.build(list(List.of(card), "200000", "500000"));

        assertThat(text).contains("хватает на все платежи");
    }

    @Test
    @DisplayName("Процент использования считается, при нулевом лимите его нет")
    void usagePercent() {
        CreditCardResponse card = card("Платинум", "4567", "250000", null, 7);

        assertThat(builder.build(list(List.of(card), "250000", "500000"))).contains("использовано 50%");
        assertThat(builder.build(list(List.of(card), "250000", "0"))).doesNotContain("использовано");
    }

    private CreditCardListResponse list(List<CreditCardResponse> cards, String totalDebt, String totalLimit) {
        return CreditCardListResponse.builder()
                .cards(cards)
                .count(cards.size())
                .totalDebt(new BigDecimal(totalDebt))
                .totalLimit(new BigDecimal(totalLimit))
                .build();
    }

    private CreditCardResponse card(String name, String last4, String debt, String paymentAmount, int daysLeft) {
        return CreditCardResponse.builder()
                .id(1L)
                .name(name)
                .last4(last4)
                .maskedNumber("•••• " + last4)
                .debt(new BigDecimal(debt))
                .limit(new BigDecimal("500000"))
                .accumulatedAmount(BigDecimal.ZERO)
                .paymentAmount(paymentAmount == null ? null : new BigDecimal(paymentAmount))
                .gracePeriodDate(LocalDate.now().plusDays(daysLeft))
                .graceDaysLeft(daysLeft)
                .build();
    }
}
