package ru.money.goldentaurusbank.www.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreditCardOperationRequest {

    @NotNull(message = "Сумма операции обязательна")
    @Positive(message = "Сумма операции должна быть больше 0")
    private BigDecimal amount;

    @Size(max = 500, message = "Комментарий не должен превышать 500 символов")
    private String comment;

    private LocalDateTime dateOperation;
}
