package ru.money.goldentaurusbank.www.backend.service.telegram;

import org.springframework.stereotype.Component;
import ru.money.goldentaurusbank.www.backend.model.dto.response.CreditCardListResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.CreditCardResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

import static ru.money.goldentaurusbank.www.backend.service.telegram.TelegramFormat.amount;
import static ru.money.goldentaurusbank.www.backend.service.telegram.TelegramFormat.daysLeft;
import static ru.money.goldentaurusbank.www.backend.service.telegram.TelegramFormat.maskedCard;
import static ru.money.goldentaurusbank.www.backend.service.telegram.TelegramFormat.shortDate;

@Component
public class TelegramSummaryBuilder {

    public String build(CreditCardListResponse list) {
        List<CreditCardResponse> cards = list.getCards() == null ? List.of() : list.getCards();
        if (cards.isEmpty()) {
            return "Карт пока нет";
        }

        StringBuilder text = new StringBuilder("Карты · %d\n\n".formatted(cards.size()));
        text.append("Общий долг: %s\n".formatted(amount(list.getTotalDebt())));
        text.append("Лимит: %s".formatted(amount(list.getTotalLimit())));

        String usage = usage(list.getTotalDebt(), list.getTotalLimit());
        if (usage != null) {
            text.append(" · использовано %s".formatted(usage));
        }
        text.append('\n');

        List<CreditCardResponse> withDebt = cards.stream()
                .filter(card -> card.getDebt() != null && card.getDebt().signum() > 0)
                .sorted(Comparator.comparing(CreditCardResponse::getGracePeriodDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        if (!withDebt.isEmpty()) {
            text.append("\nБлижайшие платежи\n");
            withDebt.forEach(card -> text.append(line(card)).append('\n'));
        }

        BigDecimal shortage = shortage(cards);
        if (shortage.signum() > 0) {
            text.append("\nНакопители: не хватает %s".formatted(amount(shortage)));
        } else {
            text.append("\nНакопители: хватает на все платежи");
        }

        return text.toString().trim();
    }

    private String line(CreditCardResponse card) {
        String head = "%s · %s".formatted(shortDate(card.getGracePeriodDate()),
                maskedCard(card.getName(), card.getLast4()));

        String money = card.getPaymentAmount() != null
                ? amount(card.getPaymentAmount())
                : "долг " + amount(card.getDebt());

        String days = daysLeft(card.getGraceDaysLeft());

        return days.isEmpty()
                ? "%s — %s".formatted(head, money)
                : "%s — %s · %s".formatted(head, money, days);
    }

    private BigDecimal shortage(List<CreditCardResponse> cards) {
        return cards.stream()
                .map(this::cardShortage)
                .filter(value -> value.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal cardShortage(CreditCardResponse card) {
        BigDecimal need = card.getPaymentAmount() != null ? card.getPaymentAmount() : card.getDebt();
        if (need == null || need.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal accumulated = card.getAccumulatedAmount() == null ? BigDecimal.ZERO : card.getAccumulatedAmount();

        return need.subtract(accumulated);
    }

    private String usage(BigDecimal debt, BigDecimal limit) {
        if (limit == null || limit.signum() <= 0) {
            return null;
        }

        BigDecimal percent = (debt == null ? BigDecimal.ZERO : debt)
                .multiply(BigDecimal.valueOf(100))
                .divide(limit, 0, RoundingMode.HALF_UP);

        return percent + "%";
    }
}
