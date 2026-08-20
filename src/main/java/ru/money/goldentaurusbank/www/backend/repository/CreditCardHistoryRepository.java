package ru.money.goldentaurusbank.www.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.money.goldentaurusbank.www.backend.model.domain.CreditCardHistory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface CreditCardHistoryRepository extends JpaRepository<CreditCardHistory, Long> {

    /*
     * Знак операции относительно долга: REPAY уводит его вниз, SPEND и
     * OPENING_DEBT — вверх. Откат — не удаление строки, а обратная запись,
     * поэтому знаковая сумма по всей истории всегда равна текущему долгу.
     */

    List<CreditCardHistory> findByCreditCardIdOrderByDateOperationDescIdDesc(Long creditCardId);

    boolean existsByReversalOfId(Long reversalOfId);

    boolean existsByBullionTransactionId(Long bullionTransactionId);

    /** Пары «откаченная операция → её обратная запись» одним запросом, а не по строке на каждую. */
    @Query("SELECT h.reversalOfId, h.id FROM CreditCardHistory h WHERE h.reversalOfId IN :ids")
    List<Object[]> findReversalsOf(@Param("ids") Collection<Long> ids);

    /** Транзакции слитков, входящие в погашения по картам: их нельзя откатывать поодиночке. */
    @Query("SELECT h.bullionTransactionId FROM CreditCardHistory h WHERE h.bullionTransactionId IN :ids")
    Set<Long> findBullionTransactionIds(@Param("ids") Collection<Long> ids);

    /**
     * Насколько долг вырос начиная с указанного момента. Долг на момент считается
     * обратным ходом от текущего — тем же приёмом, что накопления в
     * {@code TransactionService.balanceAt}: источник истины — сама карта, а не лог.
     */
    @Query(value = """
        SELECT COALESCE(SUM(CASE WHEN operation = 'REPAY' THEN -amount ELSE amount END), 0)
        FROM taurus.credit_card_history
        WHERE user_id = :userId
            AND date_operation >= :fromDate
        """, nativeQuery = true)
    BigDecimal getDebtDeltaAfter(@Param("userId") Long userId, @Param("fromDate") LocalDateTime fromDate);

    /** Изменение долга по месяцам: [первый день месяца, дельта]. */
    @Query(value = """
        SELECT
            DATE_TRUNC('month', date_operation)                                          AS month,
            COALESCE(SUM(CASE WHEN operation = 'REPAY' THEN -amount ELSE amount END), 0) AS delta
        FROM taurus.credit_card_history
        WHERE user_id = :userId
            AND date_operation >= :fromDate
            AND date_operation <= :toDate
        GROUP BY DATE_TRUNC('month', date_operation)
        ORDER BY month ASC
        """, nativeQuery = true)
    List<Object[]> getMonthlyDebtStatistics(
            @Param("userId") Long userId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );

    /** Изменение долга по дням месяца: [день, дельта]. */
    @Query(value = """
        SELECT
            EXTRACT(DAY FROM date_operation)                                             AS day,
            COALESCE(SUM(CASE WHEN operation = 'REPAY' THEN -amount ELSE amount END), 0) AS delta
        FROM taurus.credit_card_history
        WHERE user_id = :userId
            AND EXTRACT(YEAR FROM date_operation) = :year
            AND EXTRACT(MONTH FROM date_operation) = :month
        GROUP BY EXTRACT(DAY FROM date_operation)
        ORDER BY day ASC
        """, nativeQuery = true)
    List<Object[]> getDailyDebtStatistics(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month
    );
}
