package ru.money.goldentaurusbank.www.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.money.goldentaurusbank.www.backend.model.domain.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /*
     * Смысл операции выводится из заполненности ног, поэтому во всех выборках:
     *   пополнение — только target, снятие — только source, перевод — обе ноги (в дельту не идёт).
     * opening_balance исключается везде: стартовый остаток формирует базовую линию графика,
     * а не движение по нему. imported исключается только из столбиков «Доход/Расход»:
     * в Excel перераспределение между слитками неотличимо от внешнего дохода.
     */

    boolean existsByReversalOfId(Long reversalOfId);

    List<Transaction> findByBatchIdOrderByCreatedAtAsc(Long batchId);

    Optional<Transaction> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    List<Transaction> findByReversalOfIdOrIdOrderByCreatedAtAsc(Long reversalOfId, Long id);

    /** Пары «откаченная операция → её обратная запись» одним запросом, чтобы не дёргать БД на каждую строку истории. */
    @Query("SELECT t.reversalOfId, t.id FROM Transaction t WHERE t.reversalOfId IN :ids")
    List<Object[]> findReversalsOf(@Param("ids") Collection<Long> ids);

    @Query(value = """
        SELECT * FROM taurus.transactions
        WHERE source_bullion_id = :bullionId OR target_bullion_id = :bullionId
        ORDER BY date_operation DESC, id DESC
        """, nativeQuery = true)
    List<Transaction> findBullionHistory(@Param("bullionId") Long bullionId);

    @Query(value = """
        SELECT * FROM taurus.transactions
        WHERE user_id = :userId
        ORDER BY date_operation DESC, id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Transaction> findRecentTransactions(@Param("userId") Long userId, @Param("limit") int limit);

    @Query(value = """
        SELECT
            DATE_TRUNC('month', date_operation)                          AS month,
            COALESCE(SUM(amount) FILTER (WHERE source_bullion_id IS NULL
                                           AND target_bullion_id IS NOT NULL
                                           AND NOT opening_balance
                                           AND NOT imported), 0)         AS income,
            COALESCE(SUM(amount) FILTER (WHERE source_bullion_id IS NOT NULL
                                           AND target_bullion_id IS NULL
                                           AND NOT opening_balance
                                           AND NOT imported), 0)         AS expense,
            COALESCE(SUM(amount) FILTER (WHERE source_bullion_id IS NULL
                                           AND target_bullion_id IS NOT NULL
                                           AND NOT opening_balance), 0)
          - COALESCE(SUM(amount) FILTER (WHERE source_bullion_id IS NOT NULL
                                           AND target_bullion_id IS NULL
                                           AND NOT opening_balance), 0)  AS delta,
            COUNT(*)                                                     AS transaction_count
        FROM taurus.transactions
        WHERE user_id = :userId
            AND date_operation >= :fromDate
            AND date_operation <= :toDate
        GROUP BY DATE_TRUNC('month', date_operation)
        ORDER BY month ASC
        """, nativeQuery = true)
    List<Object[]> getMonthlyStatistics(
            @Param("userId") Long userId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );

    @Query(value = """
        SELECT
            EXTRACT(DAY FROM date_operation)                             AS day,
            COALESCE(SUM(amount) FILTER (WHERE source_bullion_id IS NULL
                                           AND target_bullion_id IS NOT NULL
                                           AND NOT opening_balance
                                           AND NOT imported), 0)         AS income,
            COALESCE(SUM(amount) FILTER (WHERE source_bullion_id IS NOT NULL
                                           AND target_bullion_id IS NULL
                                           AND NOT opening_balance
                                           AND NOT imported), 0)         AS expense,
            COALESCE(SUM(amount) FILTER (WHERE source_bullion_id IS NULL
                                           AND target_bullion_id IS NOT NULL
                                           AND NOT opening_balance), 0)
          - COALESCE(SUM(amount) FILTER (WHERE source_bullion_id IS NOT NULL
                                           AND target_bullion_id IS NULL
                                           AND NOT opening_balance), 0)  AS delta,
            COUNT(*)                                                     AS transaction_count
        FROM taurus.transactions
        WHERE user_id = :userId
            AND EXTRACT(YEAR FROM date_operation) = :year
            AND EXTRACT(MONTH FROM date_operation) = :month
        GROUP BY EXTRACT(DAY FROM date_operation)
        ORDER BY day ASC
        """, nativeQuery = true)
    List<Object[]> getDailyStatistics(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month
    );

    @Query(value = """
        SELECT
            COALESCE(SUM(amount) FILTER (WHERE source_bullion_id IS NULL
                                           AND target_bullion_id IS NOT NULL
                                           AND NOT opening_balance
                                           AND NOT imported), 0)         AS income,
            COALESCE(SUM(amount) FILTER (WHERE source_bullion_id IS NOT NULL
                                           AND target_bullion_id IS NULL
                                           AND NOT opening_balance
                                           AND NOT imported), 0)         AS expense,
            COUNT(*)                                                     AS transaction_count
        FROM taurus.transactions
        WHERE user_id = :userId
            AND date_operation >= :fromDate
            AND date_operation <= :toDate
        """, nativeQuery = true)
    List<Object[]> getTotalStatistics(
            @Param("userId") Long userId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );

    /**
     * Изменение накоплений за все операции начиная с указанного момента.
     * График строится обратным ходом от фактической суммы слитков, чтобы правый
     * край всегда совпадал с накоплениями на дашборде.
     */
    @Query(value = """
        SELECT
            COALESCE(SUM(amount) FILTER (WHERE source_bullion_id IS NULL
                                           AND target_bullion_id IS NOT NULL
                                           AND NOT opening_balance), 0)
          - COALESCE(SUM(amount) FILTER (WHERE source_bullion_id IS NOT NULL
                                           AND target_bullion_id IS NULL
                                           AND NOT opening_balance), 0)
        FROM taurus.transactions
        WHERE user_id = :userId
            AND date_operation >= :fromDate
        """, nativeQuery = true)
    BigDecimal getDeltaAfter(@Param("userId") Long userId, @Param("fromDate") LocalDateTime fromDate);

    @Query(value = """
        SELECT DISTINCT EXTRACT(YEAR FROM date_operation)::int AS year
        FROM taurus.transactions
        WHERE user_id = :userId
        ORDER BY year ASC
        """, nativeQuery = true)
    List<Integer> getAvailableYears(@Param("userId") Long userId);

    @Query(value = """
        SELECT * FROM taurus.transactions
        WHERE user_id = :userId
            AND (CAST(:fromDate AS TIMESTAMP) IS NULL OR date_operation >= CAST(:fromDate AS TIMESTAMP))
            AND (CAST(:toDate   AS TIMESTAMP) IS NULL OR date_operation <= CAST(:toDate   AS TIMESTAMP))
            AND (CAST(:kind AS VARCHAR) IS NULL
                 OR (CAST(:kind AS VARCHAR) = 'OPENING_BALANCE' AND opening_balance)
                 OR (CAST(:kind AS VARCHAR) = 'TRANSFER'    AND NOT opening_balance
                        AND source_bullion_id IS NOT NULL AND target_bullion_id IS NOT NULL)
                 OR (CAST(:kind AS VARCHAR) = 'DEPOSIT'     AND NOT opening_balance
                        AND source_bullion_id IS NULL     AND target_bullion_id IS NOT NULL)
                 OR (CAST(:kind AS VARCHAR) = 'WITHDRAWAL'  AND NOT opening_balance
                        AND source_bullion_id IS NOT NULL AND target_bullion_id IS NULL))
        ORDER BY date_operation DESC, id DESC
        OFFSET :offset LIMIT :limit
        """, nativeQuery = true)
    List<Transaction> findTransactionHistory(
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("kind") String kind
    );

    @Query(value = """
        SELECT COUNT(*) FROM taurus.transactions
        WHERE user_id = :userId
            AND (CAST(:fromDate AS TIMESTAMP) IS NULL OR date_operation >= CAST(:fromDate AS TIMESTAMP))
            AND (CAST(:toDate   AS TIMESTAMP) IS NULL OR date_operation <= CAST(:toDate   AS TIMESTAMP))
            AND (CAST(:kind AS VARCHAR) IS NULL
                 OR (CAST(:kind AS VARCHAR) = 'OPENING_BALANCE' AND opening_balance)
                 OR (CAST(:kind AS VARCHAR) = 'TRANSFER'    AND NOT opening_balance
                        AND source_bullion_id IS NOT NULL AND target_bullion_id IS NOT NULL)
                 OR (CAST(:kind AS VARCHAR) = 'DEPOSIT'     AND NOT opening_balance
                        AND source_bullion_id IS NULL     AND target_bullion_id IS NOT NULL)
                 OR (CAST(:kind AS VARCHAR) = 'WITHDRAWAL'  AND NOT opening_balance
                        AND source_bullion_id IS NOT NULL AND target_bullion_id IS NULL))
        """, nativeQuery = true)
    long countTransactionHistory(
            @Param("userId") Long userId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("kind") String kind
    );

    @Query(value = """
        SELECT
            CASE WHEN opening_balance THEN 'OPENING_BALANCE'
                 WHEN source_bullion_id IS NOT NULL AND target_bullion_id IS NOT NULL THEN 'TRANSFER'
                 WHEN target_bullion_id IS NOT NULL THEN 'DEPOSIT'
                 ELSE 'WITHDRAWAL'
            END                        AS kind,
            COUNT(*)                   AS count,
            COALESCE(SUM(amount), 0)   AS total_amount
        FROM taurus.transactions
        WHERE user_id = :userId
            AND date_operation >= :fromDate
            AND date_operation <= :toDate
        GROUP BY 1
        ORDER BY total_amount DESC
        """, nativeQuery = true)
    List<Object[]> getKindStatistics(
            @Param("userId") Long userId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );
}
