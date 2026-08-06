package ru.money.goldentaurusbank.www.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class WithdrawBullionRequest {

    @NotNull(message = "ID наименования обязательно")
    private Long bullionNameId;

    @NotNull(message = "ID хранилища обязательно")
    private Long vaultId;

    @NotNull(message = "Сумма списания обязательна")
    @Positive(message = "Сумма списания должна быть больше 0")
    private BigDecimal amount;

    @Size(max = 500, message = "Описание не должно превышать 500 символов")
    private String userComment;

    private LocalDateTime dateOperation;
}