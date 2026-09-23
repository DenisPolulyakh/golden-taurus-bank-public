package ru.money.goldentaurusbank.www.backend.model.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Закрытие месяца: снимок фиксируется всегда. Остаток бюджета, если он есть,
 * уходит переводом на targetBullionId — тогда поле обязательно. Без остатка
 * (0 или минус) поле не нужно, переносить нечего.
 */
@Data
public class BudgetCloseRequest {

    private Long targetBullionId;

    /** Пусто — последний день месяца */
    private LocalDateTime dateOperation;

    @Size(max = 500, message = "Комментарий не должен превышать 500 символов")
    private String comment;
}
