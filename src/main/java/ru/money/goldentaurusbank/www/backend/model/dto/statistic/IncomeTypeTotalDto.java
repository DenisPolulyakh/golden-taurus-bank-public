package ru.money.goldentaurusbank.www.backend.model.dto.statistic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.IncomeType;

import java.math.BigDecimal;

/** Итог по одному типу дохода за весь запрошенный период. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeTypeTotalDto {
    private IncomeType type;
    private String displayName;
    private BigDecimal amount;
    private Long count;
}
