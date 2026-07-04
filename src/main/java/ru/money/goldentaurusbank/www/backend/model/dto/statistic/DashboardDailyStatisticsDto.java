package ru.money.goldentaurusbank.www.backend.model.dto.statistic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDailyStatisticsDto {
    
    /**
     * Год
     */
    private Integer year;
    
    /**
     * Месяц
     */
    private Integer month;
    
    /**
     * Название месяца
     */
    private String monthLabel;
    
    /**
     * Общая сумма накоплений за месяц
     */
    private BigDecimal totalAmount;
    
    /**
     * Список дневных данных
     */
    private List<DailyDataDto> dailyData;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyDataDto {
        /**
         * День месяца (1-31)
         */
        private Integer day;
        
        /**
         * Дата в формате "дд.ММ"
         */
        private String date;
        
        /**
         * Сумма накоплений на этот день (нарастающий итог)
         */
        private BigDecimal savings;
        
        /**
         * Изменение за день (доходы - расходы)
         */
        private BigDecimal dailyChange;
        
        /**
         * Доходы за день
         */
        private BigDecimal income;
        
        /**
         * Расходы за день
         */
        private BigDecimal expense;
        
        /**
         * Количество транзакций за день
         */
        private Long transactionCount;
    }
}