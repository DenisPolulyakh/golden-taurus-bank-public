
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
    private String month;          // "2024-01"
    private String monthLabel;     // "Янв 2024"
    private BigDecimal income;     // пополнения
    private BigDecimal expense;    // списания
    private BigDecimal netChange;  // изменение (income - expense)
    private Long transactionCount; // количество транзакций
}