package ru.money.goldentaurusbank.www.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.CreditCardOperation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditCardHistoryResponse {

    private Long id;
    private CreditCardOperation operation;
    private String description;
    private BigDecimal amount;
    /** Со знаком относительно долга: погашение уводит его вниз. */
    private BigDecimal signedAmount;
    private BigDecimal debtAfter;
    private LocalDateTime dateOperation;
    private LocalDateTime createdAt;
    private String comment;
    private boolean canRollback;
    private Long reversalOfId;
    private Long reversedById;
    /** Заполнен у погашения со слитка — второй ноги операции. */
    private Long bullionTransactionId;
}
