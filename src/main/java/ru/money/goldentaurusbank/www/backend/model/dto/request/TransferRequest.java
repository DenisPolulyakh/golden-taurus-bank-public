// TransferRequest.java
package ru.money.goldentaurusbank.www.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TransferRequest {
    @NotNull(message = "ID слитка-отправителя обязателен")
    private Long fromBullionId;
    
    @NotNull(message = "ID слитка-получателя обязателен")
    private Long toBullionId;
    
    @NotNull(message = "Сумма перевода обязательна")
    @Positive(message = "Сумма должна быть больше 0")
    private BigDecimal amount;

    private LocalDateTime dateOperation;
    
    private String comment;

    /**
     * Корзина операции для месячного бюджета: {@code true} — трата дня,
     * {@code false} — движение самого бюджета (финансирование, докидывание,
     * перенос остатка). Обёртка, а не примитив: {@code null} от клиента,
     * который про поле не знает, читается как трата.
     */
    private Boolean budgetOperation;
}