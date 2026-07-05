
package ru.money.goldentaurusbank.www.backend.model.dto.statistic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyDataDto {
    private String month;
    private String monthLabel;
    private BigDecimal income;
    private BigDecimal expense;
    private BigDecimal netChange;
    private BigDecimal savings;
    private Long transactionCount;
}