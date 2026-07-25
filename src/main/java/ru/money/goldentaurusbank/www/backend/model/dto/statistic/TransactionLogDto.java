// TransactionLogDto.java
package ru.money.goldentaurusbank.www.backend.model.dto.statistic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
    private List<DescriptionSegmentDto> descriptionSegments;
    private String userComment;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime dateOperation;
    private Boolean canRollback;
    private Boolean canRedo;
    private String rollbackStatus;
    private Long parentTransactionId;
}