package ru.money.goldentaurusbank.www.backend.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.money.goldentaurusbank.www.backend.model.domain.TransactionLog;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionLogRepository extends JpaRepository<TransactionLog, Long> {

    List<TransactionLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<TransactionLog> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime from, LocalDateTime to);

    List<TransactionLog> findByParentTransactionId(Long parentTransactionId);

    List<TransactionLog> findByFromBullionIdOrderByCreatedAtDesc(Long bullionId);

    List<TransactionLog> findByToBullionIdOrderByCreatedAtDesc(Long bullionId);

    List<TransactionLog> findByBatchIdOrderByCreatedAtDesc(Long batchId);

    List<TransactionLog> findByBatchIdAndStatusOrderByCreatedAtAsc(Long batchId, String status);

    boolean existsByParentTransactionIdAndStatus(Long parentTransactionId, String status);

    List<TransactionLog> findByParentTransactionIdOrIdOrderByCreatedAtAsc(Long parentId, Long id);

    Optional<TransactionLog> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    @Query(value = """
        SELECT 
            DATE_TRUNC('month', date_operation) as month,
            COALESCE(SUM(CASE 
                WHEN operation_type = 'REFILL_BULLION' THEN amount 
                ELSE 0 
            END), 0) as total_income,
            COALESCE(SUM(CASE 
                WHEN operation_type = 'WITHDRAW_BULLION' THEN amount 
                ELSE 0 
            END), 0) as total_expense,
            COUNT(*) as transaction_count
        FROM taurus.transaction_logs
        WHERE user_id = :userId
            AND status = 'SUCCESS'
            AND date_operation >= :fromDate
            AND date_operation <= :toDate
            AND operation_type IN ('REFILL_BULLION', 'WITHDRAW_BULLION')
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
            COALESCE(SUM(CASE 
                WHEN operation_type = 'REFILL_BULLION' THEN amount 
                ELSE 0 
            END), 0) as total_income,
            COALESCE(SUM(CASE 
                WHEN operation_type = 'WITHDRAW_BULLION' THEN amount 
                ELSE 0 
            END), 0) as total_expense,
            COUNT(*) as total_transactions
        FROM taurus.transaction_logs
        WHERE user_id = :userId
            AND status = 'SUCCESS'
            AND operation_type IN ('REFILL_BULLION', 'WITHDRAW_BULLION')
            AND date_operation >= :fromDate
            AND date_operation <= :toDate
        """, nativeQuery = true)
    List<Object[]> getTotalStatistics(
            @Param("userId") Long userId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );

    @Query(value = """
        SELECT * FROM taurus.transaction_logs
        WHERE user_id = :userId 
            AND status = 'SUCCESS'
        ORDER BY date_operation DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<TransactionLog> findLastSuccessfulTransactions(
            @Param("userId") Long userId,
            @Param("limit") int limit
    );


    

    @Query(value = """
        SELECT COUNT(*) FROM taurus.transaction_logs
        WHERE user_id = :userId
            AND date_operation >= COALESCE(:dateFrom, date_operation)
            AND date_operation <= COALESCE(:toDate, date_operation)
            AND operation_type = COALESCE(:operationType, operation_type)
            AND status = COALESCE(:status, status)
        """, nativeQuery = true)
    long countTransactionHistory(
            @Param("userId") Long userId,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("toDate") LocalDateTime toDate,
            @Param("operationType") String operationType,
            @Param("status") String status
    );

    @Query(value = """
        SELECT COALESCE(SUM(amount), 0) 
        FROM taurus.transaction_logs
        WHERE user_id = :userId 
            AND operation_type = 'REFILL_BULLION' 
            AND status = 'SUCCESS'
            AND date_operation >= :fromDate 
            AND date_operation <= :toDate
        """, nativeQuery = true)
    BigDecimal getTotalIncome(
            @Param("userId") Long userId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );

    @Query(value = """
        SELECT COALESCE(SUM(amount), 0) 
        FROM taurus.transaction_logs
        WHERE user_id = :userId 
            AND operation_type = 'WITHDRAW_BULLION' 
            AND status = 'SUCCESS'
            AND date_operation >= :fromDate 
            AND date_operation <= :toDate
        """, nativeQuery = true)
    BigDecimal getTotalExpense(
            @Param("userId") Long userId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );

    @Query(value = """
        SELECT 
            operation_type,
            COUNT(*) as count,
            COALESCE(SUM(amount), 0) as total_amount
        FROM taurus.transaction_logs
        WHERE user_id = :userId
            AND status = 'SUCCESS'
            AND date_operation >= :fromDate
            AND date_operation <= :toDate
        GROUP BY operation_type
        ORDER BY total_amount DESC
        """, nativeQuery = true)
    List<Object[]> getOperationTypeStatistics(
            @Param("userId") Long userId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );


    @Query(value = """
            SELECT 
                EXTRACT(DAY FROM date_operation) as day,
                COALESCE(SUM(CASE 
                    WHEN operation_type = 'REFILL_BULLION' THEN amount 
                    ELSE 0 
                END), 0) as daily_income,
                COALESCE(SUM(CASE 
                    WHEN operation_type = 'WITHDRAW_BULLION' THEN amount 
                    ELSE 0 
                END), 0) as daily_expense,
                COUNT(*) as transaction_count
            FROM taurus.transaction_logs
            WHERE user_id = :userId
                AND status = 'SUCCESS'
                AND EXTRACT(YEAR FROM date_operation) = :year
                AND EXTRACT(MONTH FROM date_operation) = :month
                AND operation_type IN ('REFILL_BULLION', 'WITHDRAW_BULLION')
            GROUP BY EXTRACT(DAY FROM date_operation)
            ORDER BY day ASC
            """, nativeQuery = true)
    List<Object[]> getDailyStatistics(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month
    );

    @Query(value = """
        SELECT * FROM taurus.transaction_logs
        WHERE user_id = :userId
            AND date_operation >= COALESCE(:dateFrom, date_operation)
            AND date_operation <= COALESCE(:toDate, date_operation)
            AND operation_type = COALESCE(:operationType, operation_type)
            AND status = COALESCE(:status, status)
        ORDER BY date_operation DESC
        OFFSET :offset LIMIT :limit
        """, nativeQuery = true)
    List<TransactionLog> findTransactionHistory(
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("toDate") LocalDateTime toDate,
            @Param("operationType") String operationType,
            @Param("status") String status
    );
}