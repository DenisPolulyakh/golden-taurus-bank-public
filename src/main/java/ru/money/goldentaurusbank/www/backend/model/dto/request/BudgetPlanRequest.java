package ru.money.goldentaurusbank.www.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** План расходов на месяц. Ноль допустим — так план обнуляют, не удаляя строку. */
@Data
public class BudgetPlanRequest {

    @NotNull(message = "Сумма плана обязательна")
    @PositiveOrZero(message = "План не может быть отрицательным")
    private BigDecimal plannedAmount;

    @Size(max = 500, message = "Комментарий не должен превышать 500 символов")
    private String comment;
}
