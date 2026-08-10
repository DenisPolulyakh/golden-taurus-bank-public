package ru.money.goldentaurusbank.www.backend.model.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.TransactionKind;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions", schema = "taurus")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "date_operation", nullable = false)
    private LocalDateTime dateOperation;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "source_bullion_id")
    private Long sourceBullionId;

    @Column(name = "target_bullion_id")
    private Long targetBullionId;

    @Column(name = "opening_balance", nullable = false)
    @Builder.Default
    private boolean openingBalance = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean imported = false;

    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "reversal_of_id")
    private Long reversalOfId;

    @Column(name = "batch_id")
    private Long batchId;

    @Transient
    public TransactionKind getKind() {
        if (openingBalance) {
            return TransactionKind.OPENING_BALANCE;
        }
        if (sourceBullionId != null && targetBullionId != null) {
            return TransactionKind.TRANSFER;
        }
        if (targetBullionId != null) {
            return TransactionKind.DEPOSIT;
        }
        return TransactionKind.WITHDRAWAL;
    }

    /**
     * Сумма со знаком относительно накоплений: перевод и стартовый остаток не двигают их.
     */
    @Transient
    public BigDecimal getSignedAmount() {
        return switch (getKind()) {
            case DEPOSIT -> amount;
            case WITHDRAWAL -> amount.negate();
            case TRANSFER, OPENING_BALANCE -> BigDecimal.ZERO;
        };
    }
}
