package ru.money.goldentaurusbank.www.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Переразметка записанной операции: в какую корзину бюджета её положить. */
@Data
public class BudgetBucketRequest {

    @NotNull(message = "Корзина операции обязательна")
    private Boolean budgetOperation;
}
