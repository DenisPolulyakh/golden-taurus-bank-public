package ru.money.goldentaurusbank.www.backend.model.dto.statistic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.IncomeType;

import java.math.BigDecimal;
import java.util.Map;

/** Одна точка графика: день, месяц или год — в зависимости от granularity запроса. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeStatPointDto {
    /** Машиночитаемый период: "2026-09-15", "2026-09" или "2026". */
    private String period;
    /** Человекочитаемая подпись для графика. */
    private String periodLabel;
    private BigDecimal total;
    /** Сумма по типам дохода за эту точку; типов без дохода в точке нет. */
    private Map<IncomeType, BigDecimal> byType;
}
