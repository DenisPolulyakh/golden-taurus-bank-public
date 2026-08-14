package ru.money.goldentaurusbank.www.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BullionRequest {

    @NotNull(message = "ID наименования обязательно")
    private Long bullionNameId;

    @NotNull(message = "ID хранилища обязательно")
    private Long vaultId;

    @NotNull(message = "Сумма обязательна")
    @PositiveOrZero(message = "Сумма не может быть отрицательной")
    private BigDecimal amount;

    @Size(max = 500, message = "Описание не должно превышать 500 символов")
    private String description;

    private LocalDateTime dateOperation;

    @Size(max = 500, message = "Комментарий не должен превышать 500 символов")
    private String userComment;
}