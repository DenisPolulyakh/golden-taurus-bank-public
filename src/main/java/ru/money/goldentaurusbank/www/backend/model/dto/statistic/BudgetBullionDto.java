package ru.money.goldentaurusbank.www.backend.model.dto.statistic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Слиток в отчёте о бюджете — ровно то, что нужно шапке экрана, без остального. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetBullionDto {

    private Long id;

    /** Наименование слитка */
    private String title;

    /** Хранилище, где он лежит — одно наименование бывает в нескольких хранилищах */
    private String vaultName;

    /** Текущая сумма слитка, для сверки с остатком бюджета */
    private BigDecimal amount;

    private boolean archived;
}
