
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
    private BigDecimal totalAmount; // сумма всех слитков пользователя
    private BigDecimal totalDebt;   // текущая задолженность по всем кредитным картам
    private Long totalTransactions;
    private List<TransactionDto> recentTransactions;
    private List<KindStatsDto> kindStats;
}