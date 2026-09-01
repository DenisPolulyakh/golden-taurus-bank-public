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
     */
    @Column(name = "planned_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal plannedAmount = BigDecimal.ZERO;

    @Column(name = "comment", length = 500)
    private String comment;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


}
