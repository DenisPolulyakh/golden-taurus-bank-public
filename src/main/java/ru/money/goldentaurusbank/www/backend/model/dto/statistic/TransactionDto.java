package ru.money.goldentaurusbank.www.backend.model.dto.statistic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.IncomeType;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.TransactionKind;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDto {
    private Long id;
    private TransactionKind kind;
    private Long sourceBullionId;
    private Long targetBullionId;
    private BigDecimal amount;
    /** Сумма со знаком относительно накоплений: у перевода и стартового остатка — 0. */
    private BigDecimal signedAmount;
    private String description;
    private List<DescriptionSegmentDto> descriptionSegments;
    private String comment;
    /** Тип дохода — заполнен только у классифицированного пополнения. */
    private IncomeType incomeType;
    /** Можно ли назначить тип дохода: только своё пополнение, не откат и не откачено. */
    private boolean canChangeIncomeType;
    private LocalDateTime createdAt;
    private LocalDateTime dateOperation;
    private boolean imported;
    /**
     * Корзина операции в месячном бюджете: true — трата дня, false — движение
     * самого бюджета. Осмысленно только у операций по бюджетному слитку.
     */
    private boolean budgetOperation;
    private boolean canRollback;
    /** Заполнено у записи, которая сама является откатом другой операции. */
    private Long reversalOfId;
    /** Id обратной транзакции, если эта операция уже откачена. */
    private Long reversedById;
    /**
     * Нога погашения по кредитной карте: откатывать её поодиночке нельзя,
     * кнопка отката живёт в истории карты.
     */
    private boolean lockedByCard;
    /** Остаток слитка сразу после этой операции. Заполнен только в истории слитка. */
    private BigDecimal balanceAfter;
}
