package ru.money.goldentaurusbank.www.backend.model.dto.statistic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Отчёт «Бюджет на месяц» по одному слитку.
 * <p>
 * Столбик сверки читается сверху вниз:
 * <pre>
 *   openingBalance + funding = available
 *   available − spent        = closingBalance   ← сходится с остатком слитка
 * </pre>
 * Это арифметическое тождество, а не правило: каждая операция по слитку учтена
 * ровно один раз — либо в {@code spent}, либо в {@code funding}. Расхождение
 * {@code discrepancy} означает баг в подсчёте, а не ошибку пользователя.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetMonthDto {

    private Integer year;

    private Integer month;

    /** «сентябрь 2026» */
    private String monthLabel;

    /** Слиток, по которому построен отчёт; null — бюджетный слиток не выбран */
    private BudgetBullionDto budgetBullion;

    /** Слиток-источник финансирования; null — не выбран */
    private BudgetBullionDto sourceBullion;

    /** План месяца; null — не задан, тогда перерасход не считается */
    private BigDecimal plannedAmount;

    /** Комментарий к плану месяца */
    private String planComment;

    /** Остаток слитка на 1-е число */
    private BigDecimal openingBalance;

    /** Все движения бюджета за месяц со знаком: пришло минус ушло */
    private BigDecimal funding;

    /** Доложено сверх плана: {@code max(0, funding − plannedAmount)} */
    private BigDecimal extraFunding;

    /** Сколько можно было потратить: {@code openingBalance + funding} */
    private BigDecimal available;

    /** Потрачено за месяц */
    private BigDecimal spent;

    /**
     * Перерасход относительно плана: {@code spent − plannedAmount}.
     * Бывает и без докидывания — если остаток прошлого месяца не увели,
     * доступно было больше плана. null, если план не задан.
     */
    private BigDecimal overspend;

    /** Остаток бюджета на конец месяца: {@code available − spent} */
    private BigDecimal closingBalance;

    /**
     * Контрольный остаток слитка на конец месяца, посчитанный независимо —
     * прямой суммой всех операций слитка до 1-го числа следующего месяца.
     */
    private BigDecimal controlBalance;

    /**
     * {@code closingBalance − controlBalance}. Ноль при исправном подсчёте;
     * ненулевое значение экран показывает красной плашкой.
     */
    private BigDecimal discrepancy;

    /** Текущая сумма слитка на сейчас — для сверки открытого месяца */
    private BigDecimal bullionAmount;

    /** Строка на каждый календарный день месяца */
    private List<BudgetDayDto> days;
}
