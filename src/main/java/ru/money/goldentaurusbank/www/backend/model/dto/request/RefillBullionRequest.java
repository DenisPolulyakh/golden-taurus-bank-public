package ru.money.goldentaurusbank.www.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RefillBullionRequest implements ChangeBullionRequest{

    @NotNull(message = "ID категории обязательно")
    private Long categoryId;

    @NotNull(message = "ID хранилища обязательно")
    private Long vaultId;

    @NotNull(message = "Сумма пополнения обязательна")
    @Positive(message = "Сумма пополнения должна быть больше 0")
    private BigDecimal amount;

    @Size(max = 500, message = "Описание не должно превышать 500 символов")
    private String userComment;
}