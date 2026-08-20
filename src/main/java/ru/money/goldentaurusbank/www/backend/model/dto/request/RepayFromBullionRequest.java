package ru.money.goldentaurusbank.www.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Погашение долга карты деньгами из её накопителя: одна кнопка — две операции,
 * списание со слитка и погашение по карте.
 */
@Data
public class RepayFromBullionRequest {

    @NotNull(message = "ID слитка обязателен")
    private Long bullionId;

    @NotNull(message = "Сумма погашения обязательна")
    @Positive(message = "Сумма погашения должна быть больше 0")
    private BigDecimal amount;

    @Size(max = 500, message = "Комментарий не должен превышать 500 символов")
    private String comment;

    private LocalDateTime dateOperation;
}
