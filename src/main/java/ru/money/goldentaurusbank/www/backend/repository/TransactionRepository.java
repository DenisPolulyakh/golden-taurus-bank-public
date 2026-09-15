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

    /*
     * ------------------------------------------------------------------
     * Бюджет на месяц. Считается по одному слитку, поэтому фильтр идёт по
     * bullionId, а не по флагу budget: так ручка умеет показать отчёт и по
     * слитку, который бюджетным ещё не назначен.
     *
     * Отличия от статистики дашборда:
     *   - imported НЕ исключается: импортированные операции реально двигают
     *     остаток слитка, и без них тождество «остаток = приход − расход»
     *     не сойдётся;
     *   - opening_balance НЕ исключается: стартовый остаток это деньги на
     *     слитке, просто он лежит в корзине «движение бюджета»;
     *   - тип операции не значит ничего, всё решает budget_operation.
     *
     * Пара «операция + её откат» внутри ОДНОГО месяца выбрасывается: на суммы
     * она не влияет (гасит сама себя), но раздувает валовые колонки — из-за
     * такой пары день показывал «снято 140 619 / вернул 138 339» вместо
     * «снято 5 000». Пару, разорванную границей месяца, не трогаем: там каждая
     * половина реально двигает остаток своего месяца.
     */
    String BUDGET_TX_FILTER = """
        WHERE t.user_id = :userId
            AND (t.source_bullion_id = :bullionId OR t.target_bullion_id = :bullionId)
            AND NOT (
                (t.reversal_of_id IS NOT NULL AND EXISTS (
                    SELECT 1 FROM taurus.transactions o
                    WHERE o.id = t.reversal_of_id
                      AND DATE_TRUNC('month', o.date_operation) = DATE_TRUNC('month', t.date_operation)))
                OR EXISTS (
                    SELECT 1 FROM taurus.transactions r
                    WHERE r.reversal_of_id = t.id
                      AND DATE_TRUNC('month', r.date_operation) = DATE_TRUNC('month', t.date_operation))
            )
            """;

    /**
     * Разбивка бюджетного слитка по дням месяца:
     * снято / вернул / возмещено (нетто переводов) / движение бюджета.
     */
    @Query(value = """
        SELECT
            EXTRACT(DAY FROM t.date_operation)                                  AS day,

            COALESCE(SUM(t.amount) FILTER (WHERE t.budget_operation
                AND t.source_bullion_id = :bullionId
                AND t.target_bullion_id IS NULL), 0)                            AS taken,

            COALESCE(SUM(t.amount) FILTER (WHERE t.budget_operation
                AND t.target_bullion_id = :bullionId
                AND t.source_bullion_id IS NULL), 0)                            AS returned,

            COALESCE(SUM(t.amount) FILTER (WHERE t.budget_operation
                AND t.target_bullion_id = :bullionId
                AND t.source_bullion_id IS NOT NULL), 0)
          - COALESCE(SUM(t.amount) FILTER (WHERE t.budget_operation
                AND t.source_bullion_id = :bullionId
                AND t.target_bullion_id IS NOT NULL), 0)                        AS compensated,

            COALESCE(SUM(t.amount) FILTER (WHERE NOT t.budget_operation
                AND t.target_bullion_id = :bullionId), 0)
          - COALESCE(SUM(t.amount) FILTER (WHERE NOT t.budget_operation
                AND t.source_bullion_id = :bullionId), 0)                       AS funding,

            COUNT(*)                                                            AS transaction_count
        FROM taurus.transactions t
        """ + BUDGET_TX_FILTER + """
            AND EXTRACT(YEAR FROM t.date_operation) = :year
            AND EXTRACT(MONTH FROM t.date_operation) = :month
        GROUP BY EXTRACT(DAY FROM t.date_operation)
        ORDER BY day ASC
        """, nativeQuery = true)
    List<Object[]> getBudgetDailyStatistics(
            @Param("userId") Long userId,
            @Param("bullionId") Long bullionId,
            @Param("year") int year,
            @Param("month") int month
    );

    /**
     * Остаток бюджетного слитка на начало периода — сумма всех ног строго раньше
     * указанного момента. Здесь не выбрасывается ничего: каждая записанная
     * операция реально подвинула остаток, включая половинки откатных пар.
     */
    @Query(value = """
        SELECT COALESCE(SUM(amount) FILTER (WHERE target_bullion_id = :bullionId), 0)
             - COALESCE(SUM(amount) FILTER (WHERE source_bullion_id = :bullionId), 0)
        FROM taurus.transactions
        WHERE user_id = :userId
            AND (source_bullion_id = :bullionId OR target_bullion_id = :bullionId)
            AND date_operation < :before
        """, nativeQuery = true)
    BigDecimal getBudgetBalanceBefore(
            @Param("userId") Long userId,
            @Param("bullionId") Long bullionId,
            @Param("before") LocalDateTime before
    );

    /** Операции бюджетного слитка за один день — раскрытая строка таблицы отчёта. */
    @Query(value = """
        SELECT * FROM taurus.transactions
        WHERE user_id = :userId
            AND (source_bullion_id = :bullionId OR target_bullion_id = :bullionId)
            AND date_operation >= :from
            AND date_operation < :to
        ORDER BY date_operation ASC, id ASC
        """, nativeQuery = true)
    List<Transaction> findBudgetTransactionsBetween(
            @Param("userId") Long userId,
            @Param("bullionId") Long bullionId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    /** То же самое, но свёрнутое по месяцам года — для годовой вкладки отчёта. */
    @Query(value = """
        SELECT
            EXTRACT(MONTH FROM t.date_operation)                                AS month,

            COALESCE(SUM(t.amount) FILTER (WHERE t.budget_operation
                AND t.source_bullion_id = :bullionId), 0)
          - COALESCE(SUM(t.amount) FILTER (WHERE t.budget_operation
                AND t.target_bullion_id = :bullionId), 0)                       AS spent,

            COALESCE(SUM(t.amount) FILTER (WHERE NOT t.budget_operation
                AND t.target_bullion_id = :bullionId), 0)
          - COALESCE(SUM(t.amount) FILTER (WHERE NOT t.budget_operation
                AND t.source_bullion_id = :bullionId), 0)                       AS funding,

            COUNT(*)                                                            AS transaction_count
        FROM taurus.transactions t
        """ + BUDGET_TX_FILTER + """
            AND EXTRACT(YEAR FROM t.date_operation) = :year
        GROUP BY EXTRACT(MONTH FROM t.date_operation)
        ORDER BY month ASC
        """, nativeQuery = true)
    List<Object[]> getBudgetMonthlyStatistics(
            @Param("userId") Long userId,
            @Param("bullionId") Long bullionId,
            @Param("year") int year
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

    /**
     * Суммы по классифицированным доходам, сгруппированные по типу и по бакету
     * даты ({@code unit} = 'day' | 'month' | 'year', см. date_trunc). Только
     * пополнения с заполненным income_type — этим условием отсекаются переводы,
     * снятия и стартовый остаток (у них income_type всегда NULL по ограничению БД).
     * Откаченные пополнения (на которые есть реверс) не считаются.
     */
    @Query(value = """
        SELECT
            date_trunc(:unit, t.date_operation) AS bucket,
            t.income_type,
            SUM(t.amount) AS total,
            COUNT(*) AS cnt
        FROM taurus.transactions t
        WHERE t.user_id = :userId
            AND t.income_type IS NOT NULL
            AND t.date_operation >= :fromDate
            AND t.date_operation <= :toDate
            AND NOT EXISTS (
                SELECT 1 FROM taurus.transactions r WHERE r.reversal_of_id = t.id
            )
        GROUP BY bucket, income_type
        ORDER BY bucket
        """, nativeQuery = true)
    List<Object[]> getIncomeStatistics(
            @Param("userId") Long userId,
            @Param("unit") String unit,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );

    /** Дата самого раннего классифицированного дохода — начало диапазона для графика по годам. */
    @Query(value = """
        SELECT MIN(date_operation) FROM taurus.transactions
        WHERE user_id = :userId AND income_type IS NOT NULL
        """, nativeQuery = true)
    LocalDateTime findEarliestIncomeDate(@Param("userId") Long userId);
}
