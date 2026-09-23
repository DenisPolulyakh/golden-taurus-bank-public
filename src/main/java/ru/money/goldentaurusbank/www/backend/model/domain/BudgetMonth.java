package ru.money.goldentaurusbank.www.backend.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;


/**
 * План расходов на месяц — «Бюджет» из блока месячного листа в Excel.
 * <p>
 * Строка заводится лениво: пока пользователь не задал сумму, её нет, и отчёт
 * считается без плана. Привязки к слитку нет намеренно — план это свойство
 * месяца пользователя, а не слитка, поэтому смена бюджетного слитка не рушит
 * планы прошлых месяцев и годовую сводку.
 */
@Entity
@Table(name = "budget_months", schema = "taurus")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetMonth {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "month", nullable = false)
    private Integer month;


    /**
     * Ориентир, а не лимит: потратить и профинансировать сверх него можно,
     * разница показывается в отчёте как перерасход и «доложено сверх плана».
     * Может быть не задан — тогда отчёт считается без плана и перерасхода.
     */
    @Column(name = "planned_amount", precision = 19, scale = 2)
    private BigDecimal plannedAmount;

    @Column(name = "comment", length = 500)
    private String comment;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ------------------------------------------------------------------
    // Закрытие месяца
    // ------------------------------------------------------------------

    /**
     * NULL — месяц открыт. Момент, когда closeMonth зафиксировал снимок;
     * reopenMonth снимает вместе со всеми полями ниже.
     */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /**
     * Каким слитком считали снимок — на случай, если бюджетный слиток потом
     * сменят в настройках: прошлые снимки не должны переехать на новый слиток.
     */
    @Column(name = "budget_bullion_id")
    private Long budgetBullionId;

    @Column(name = "snapshot_planned", precision = 19, scale = 2)
    private BigDecimal snapshotPlanned;

    @Column(name = "snapshot_opening_balance", precision = 19, scale = 2)
    private BigDecimal snapshotOpeningBalance;

    @Column(name = "snapshot_funding", precision = 19, scale = 2)
    private BigDecimal snapshotFunding;

    @Column(name = "snapshot_spent", precision = 19, scale = 2)
    private BigDecimal snapshotSpent;

    @Column(name = "snapshot_closing_balance", precision = 19, scale = 2)
    private BigDecimal snapshotClosingBalance;

    /** Сумма остатка, уведённая на другой слиток при закрытии. NULL/0, если остатка не было. */
    @Column(name = "remainder_transferred", precision = 19, scale = 2)
    private BigDecimal remainderTransferred;

    @Column(name = "remainder_target_bullion_id")
    private Long remainderTargetBullionId;

    /** Транзакция перевода остатка — откатывается через неё при reopenMonth. */
    @Column(name = "close_transaction_id")
    private Long closeTransactionId;

    public boolean isClosed() {
        return closedAt != null;
    }
}
