// TransactionLogDto.java
package ru.money.goldentaurusbank.www.backend.model.dto.statistic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionLogDto {
    private Long id;
    private String operationType;
    private Long fromBullionId;
    private Long toBullionId;
    private BigDecimal amount;
    private String description;
    private String userComment;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private Boolean canRollback;     // можно ли откатить
    private Boolean canRedo;         // можно ли повторить
    private String rollbackStatus;   // ROLLED_BACK / REDONE / null
    private Long parentTransactionId;
}