package ru.money.goldentaurusbank.www.backend.model.dto.statistic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** Годовая сводка бюджета: 12 строк и итоги. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetYearDto {

    private Integer year;

    private BudgetBullionDto budgetBullion;

    /** Всегда 12 строк, включая месяцы без операций */
    private List<BudgetYearMonthDto> months;

    private BigDecimal totalPlanned;

    private BigDecimal totalFunding;

    private BigDecimal totalSpent;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetYearMonthDto {

        private Integer month;

        /** «август» */
        private String monthLabel;

        /** null, если план на месяц не задан */
        private BigDecimal plannedAmount;

        private BigDecimal funding;

        private BigDecimal spent;

        /** {@code spent − plannedAmount}; null, если плана нет */
        private BigDecimal overspend;

        /** Остаток бюджета на конец месяца */
        private BigDecimal closingBalance;

        private Long transactionCount;

        /** Месяц закрыт closeMonth */
        private boolean closed;

        /** Живые цифры разошлись со снимком закрытия — см. BudgetMonthDto.snapshotMismatch */
        private boolean snapshotMismatch;
    }
}
