// TransferRequest.java
package ru.money.goldentaurusbank.www.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransferRequest {
    @NotNull(message = "ID слитка-отправителя обязателен")
    private Long fromBullionId;
    
    @NotNull(message = "ID слитка-получателя обязателен")
    private Long toBullionId;
    
    @NotNull(message = "Сумма перевода обязательна")
    @Positive(message = "Сумма должна быть больше 0")
    private BigDecimal amount;
    
    private String comment;
}