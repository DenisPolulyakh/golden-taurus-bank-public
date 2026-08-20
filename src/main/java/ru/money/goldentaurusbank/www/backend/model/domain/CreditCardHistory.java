package ru.money.goldentaurusbank.www.backend.model.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.CreditCardOperation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Операция по кредитной карте. Плоская, как {@link Transaction}: карта и
 * пользователь лежат идентификаторами, без связей — история читается пачками,
 * и ленивые ссылки здесь только мешали бы.
 */
@Entity
@Table(name = "credit_card_history", schema = "taurus")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditCardHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credit_card_id", nullable = false)
    private Long creditCardId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CreditCardOperation operation;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Долг после операции: история показывает его, не пересчитывая цепочку. */
    @Column(name = "debt_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal debtAfter;

    @Column(name = "date_operation", nullable = false)
    private LocalDateTime dateOperation;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(length = 500)
    private String comment;

    @Column(name = "reversal_of_id")
    private Long reversalOfId;

    /**
     * Погашение со слитка: вторая нога операции в {@code taurus.transactions}.
     * Пока ссылка стоит, ту транзакцию нельзя откатить в одиночку — иначе слиток
     * вернёт деньги, а долг останется погашенным.
     */
    @Column(name = "bullion_transaction_id")
    private Long bullionTransactionId;

    /** Сумма со знаком относительно долга: погашение уводит его вниз. */
    @Transient
    public BigDecimal getSignedAmount() {
        return operation.increasesDebt() ? amount : amount.negate();
    }
}
