package ru.money.goldentaurusbank.www.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/** Закрытие месяца: остаток бюджета уходит переводом на выбранный слиток. */
@Data
public class BudgetCloseRequest {

    @NotNull(message = "Слиток, куда уводить остаток, обязателен")
    private Long targetBullionId;

    /** Пусто — последний день месяца */
    private LocalDateTime dateOperation;

    @Size(max = 500, message = "Комментарий не должен превышать 500 символов")
    private String comment;
}
