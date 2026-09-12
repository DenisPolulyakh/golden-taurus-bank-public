package ru.money.goldentaurusbank.www.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Карта для фронта. Полного номера здесь нет намеренно — только маска: номер
 * незачем возить по сети, а форма правки обходится пустым полем.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditCardResponse {

    private Long id;
    private String name;
    /** «•••• 4321». */
    private String maskedNumber;
    private String last4;
    private LocalDate gracePeriodDate;
    private BigDecimal paymentAmount;
    /**
     * Дней до конца льготного периода; {@code null}, если период не задан,
     * отрицательное — просрочен. Считает бэк, чтобы цвет счётчика не зависел
     * от часов клиента.
     */
    private Integer graceDaysLeft;
    private BigDecimal limit;
    private BigDecimal debt;
    /** Лимит минус долг. */
    private BigDecimal remainder;
    /** Сумма в слитках-накопителях. */
    private BigDecimal accumulatedAmount;
    /** Накоплено минус долг: минус — не хватает, плюс — хватает. */
    private BigDecimal imbalance;
    private List<AccumulatorInfo> accumulators;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccumulatorInfo {
        private Long bullionId;
        private String bullionNameTitle;
        private String bullionNameColor;
        private Long vaultId;
        private String vaultName;
        private BigDecimal amount;
    }
}
