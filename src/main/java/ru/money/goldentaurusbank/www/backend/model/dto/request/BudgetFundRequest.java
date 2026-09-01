package ru.money.goldentaurusbank.www.backend.model.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Финансирование месяца. Все поля необязательные — пустой запрос означает
 * «добрать до плана 1-го числа со слитка-источника из настроек».
 */
@Data
public class BudgetFundRequest {

    /** Сумма; пусто — добрать до плана. Сверх плана докладывать можно */
    @Positive(message = "Сумма финансирования должна быть больше 0")
    private BigDecimal amount;

    /** Слиток-источник; пусто — из настроек */
    private Long sourceBullionId;

    /**
     * Дата; пусто — 1-е число месяца для добора до плана и «сейчас» для
     * докидывания. Иначе колонка «Остаток» покажет деньги там, где их ещё не было.
     */
    private LocalDateTime dateOperation;

    @Size(max = 500, message = "Комментарий не должен превышать 500 символов")
    private String comment;
}
