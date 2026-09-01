package ru.money.goldentaurusbank.www.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RefillBullionRequest implements ChangeBullionRequest {

    @NotNull(message = "ID наименования обязательно")
    private Long bullionNameId;

    @NotNull(message = "ID хранилища обязательно")
    private Long vaultId;

    @NotNull(message = "Сумма пополнения обязательна")
    @Positive(message = "Сумма пополнения должна быть больше 0")
    private BigDecimal amount;

    @Size(max = 500, message = "Описание не должно превышать 500 символов")
    private String userComment;


    private LocalDateTime dateOperation;

    /**
     * Корзина операции для месячного бюджета: {@code true} — трата дня,
     * {@code false} — движение самого бюджета (финансирование, докидывание,
     * перенос остатка). Обёртка, а не примитив: {@code null} от клиента,
     * который про поле не знает, читается как трата.
     */
    private Boolean budgetOperation;
}