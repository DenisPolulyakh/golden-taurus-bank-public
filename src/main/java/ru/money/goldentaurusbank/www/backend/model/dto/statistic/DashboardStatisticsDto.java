
package ru.money.goldentaurusbank.www.backend.model.dto.statistic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatisticsDto {
    private List<MonthlyDataDto> monthlyData;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netChange;
    private Long totalTransactions;
    private List<TransactionLogDto> recentTransactions;
    private OperationTypeStatsDto operationTypeStats;
}