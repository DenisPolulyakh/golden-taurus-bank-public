package ru.money.goldentaurusbank.www.backend.service.telegram;

import ru.money.goldentaurusbank.www.backend.model.domain.CreditCard;

import java.math.BigDecimal;
import java.util.List;

import static ru.money.goldentaurusbank.www.backend.service.telegram.TelegramFormat.amount;
import static ru.money.goldentaurusbank.www.backend.service.telegram.TelegramFormat.fullDate;
import static ru.money.goldentaurusbank.www.backend.service.telegram.TelegramFormat.maskedCard;

public final class TelegramReminderMessages {

    private TelegramReminderMessages() {
    }

    public static String before7(CreditCard card) {
        StringBuilder text = new StringBuilder("Платёж через 7 дней · %s\n".formatted(title(card)));
        text.append("До %s\n\n".formatted(fullDate(card.getGracePeriodDate())));

        if (card.getPaymentAmount() != null) {
            text.append("К внесению: %s\n".formatted(amount(card.getPaymentAmount())));
        }

        text.append(accumulated(card)).append('\n');
        text.append("Задолженность: %s".formatted(amount(card.getDebt())));

        return text.toString();
    }

    public static String before2(CreditCard card) {
        String money = card.getPaymentAmount() != null
                ? "к внесению %s".formatted(amount(card.getPaymentAmount()))
                : "задолженность %s".formatted(amount(card.getDebt()));

        return """
                Платёж через 2 дня · %s
                До %s · %s

                Оплатите и внесите погашение в GoldenTaurusBank"""
                .formatted(title(card), fullDate(card.getGracePeriodDate()), money);
    }

    public static String before1(CreditCard card) {
        String money = card.getPaymentAmount() != null
                ? "К внесению: %s".formatted(amount(card.getPaymentAmount()))
                : "Задолженность: %s".formatted(amount(card.getDebt()));

        return """
                Платёж завтра · %s
                %s

                Последнее напоминание""".formatted(title(card), money);
    }

    public static String overdue(CreditCard card) {
        return """
                Дата платежа прошла · %s
                Было %s, погашений не было

                Внесите погашение и укажите дату следующего платежа"""
                .formatted(title(card), fullDate(card.getGracePeriodDate()));
    }

    public static String noDate(List<CreditCard> cards) {
        StringBuilder text = new StringBuilder("Без даты платежа: %d %s\n\n"
                .formatted(cards.size(), cards.size() == 1 ? "карта" : "карты"));

        cards.forEach(card -> text.append(title(card))
                .append(" — долг ")
                .append(amount(card.getDebt()))
                .append('\n'));

        text.append("\nУкажите даты, иначе напоминания по ним не приходят");

        return text.toString();
    }

    private static String accumulated(CreditCard card) {
        BigDecimal need = card.getPaymentAmount() != null ? card.getPaymentAmount() : card.getDebt();
        BigDecimal saved = card.getAccumulatedAmount();
        BigDecimal shortage = need.subtract(saved);

        return shortage.signum() > 0
                ? "Накоплено: %s — не хватает %s".formatted(amount(saved), amount(shortage))
                : "Накоплено: %s — хватает".formatted(amount(saved));
    }

    private static String title(CreditCard card) {
        return maskedCard(card.getName(), card.getLast4());
    }
}
