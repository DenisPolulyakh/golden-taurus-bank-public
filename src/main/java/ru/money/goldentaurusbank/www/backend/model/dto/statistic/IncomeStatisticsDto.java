package ru.money.goldentaurusbank.www.backend.model.dto.statistic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.IncomeStatGranularity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeStatisticsDto {
    private IncomeStatGranularity granularity;
    private LocalDate from;
    private LocalDate to;
    private BigDecimal total;
    private List<IncomeTypeTotalDto> byType;
    private List<IncomeStatPointDto> points;
}
