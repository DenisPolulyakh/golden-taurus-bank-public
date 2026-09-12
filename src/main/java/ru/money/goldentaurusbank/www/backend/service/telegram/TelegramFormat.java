package ru.money.goldentaurusbank.www.backend.service.telegram;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class TelegramFormat {

    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private TelegramFormat() {
    }

    public static String amount(BigDecimal value) {
        BigDecimal number = value == null ? BigDecimal.ZERO : value;
        boolean whole = number.stripTrailingZeros().scale() <= 0;
        String pattern = whole ? "%,.0f" : "%,.2f";

        return String.format(Locale.US, pattern, number)
                .replace(",", " ")
                .replace(".", ",")
                + " ₽";
    }

    public static String signedAmount(BigDecimal value) {
        BigDecimal number = value == null ? BigDecimal.ZERO : value;
        String sign = number.signum() < 0 ? "−" : "+";

        return sign + amount(number.abs());
    }

    public static String shortDate(LocalDate date) {
        return date == null ? "—" : date.format(SHORT_DATE);
    }

    public static String fullDate(LocalDate date) {
        return date == null ? "—" : date.format(FULL_DATE);
    }

    public static String maskedCard(String name, String last4) {
        return last4 == null || last4.isBlank() ? name : "%s ****%s".formatted(name, last4);
    }

    public static String daysLeft(Integer days) {
        if (days == null) {
            return "";
        }
        if (days < 0) {
            return "просрочено на %d %s".formatted(-days, dayWord(-days));
        }
        if (days == 0) {
            return "сегодня";
        }
        if (days == 1) {
            return "завтра";
        }

        return "через %d %s".formatted(days, dayWord(days));
    }

    private static String dayWord(int days) {
        int tail = days % 100;
        if (tail >= 11 && tail <= 14) {
            return "дней";
        }

        return switch (days % 10) {
            case 1 -> "день";
            case 2, 3, 4 -> "дня";
            default -> "дней";
        };
    }
}
