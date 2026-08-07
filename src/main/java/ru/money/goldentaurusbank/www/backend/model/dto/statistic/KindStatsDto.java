package ru.money.goldentaurusbank.www.backend.model.dto.statistic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.TransactionKind;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KindStatsDto {
    private TransactionKind kind;
    private Long count;
    private BigDecimal totalAmount;
}
