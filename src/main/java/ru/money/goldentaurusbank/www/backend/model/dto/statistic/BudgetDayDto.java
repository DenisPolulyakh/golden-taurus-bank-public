package ru.money.goldentaurusbank.www.backend.model.dto.statistic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Один день месяца в таблице бюджета. Строка есть на каждый календарный день,
 * даже пустой: в Excel дни без трат пропущены, и нумерация «траншей» сбивается.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetDayDto {

    /** День месяца, 1–31 */
    private Integer day;

    /** Дата в формате «дд.ММ» */
    private String date;

    /** Снято с бюджетного слитка наружу — утреннее снятие наличных */
    private BigDecimal taken;

    /** Внесено обратно — вечерняя сдача */
    private BigDecimal returned;

    /**
     * Нетто переводов: пришло с других слитков минус ушло на них. Отрицательное
     * значение — заплатили бюджетными за чужую категорию и вернули деньги туда.
     */
    private BigDecimal compensated;

    /**
     * Потрачено за день: {@code taken − returned − compensated}.
     * Может быть отрицательным, если возмещений было больше трат.
     */
    private BigDecimal spent;

    /**
     * Движение самого бюджета за день со знаком: финансирование, докидывание,
     * перенос остатка. В траты не входит.
     */
    private BigDecimal funding;

    /** Остаток бюджета на конец дня, нарастающим итогом */
    private BigDecimal balance;

    private Long transactionCount;
}
