package ru.money.goldentaurusbank.www.backend.model.dto.statistic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Снимок месяца на момент закрытия — «на бумаге» цифры closeMonth, в отличие
 * от живых полей {@link BudgetMonthDto}, которые пересчитываются заново при
 * каждом запросе отчёта.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetMonthSnapshotDto {

    private LocalDateTime closedAt;

    /** План на момент закрытия; null, если план не был задан */
    private BigDecimal plannedAmount;

    private BigDecimal openingBalance;

    private BigDecimal funding;

    private BigDecimal spent;

    private BigDecimal closingBalance;

    /** Остаток, уведённый на другой слиток при закрытии; null — остатка не было */
    private BigDecimal remainderTransferred;

    /** Слиток, куда ушёл остаток; null — остатка не было */
    private BudgetBullionDto remainderTargetBullion;
}
