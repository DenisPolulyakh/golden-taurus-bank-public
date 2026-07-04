package ru.money.goldentaurusbank.www.backend.model.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_logs", schema = "taurus")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 50)
    private String operationType;
    
    private Long fromBullionId;
    
    private Long toBullionId;
    
    private Long fromVaultId;
    
    private Long toVaultId;
    
    private Long categoryId;
    
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(precision = 19, scale = 2)
    private BigDecimal fromBullionAmountBefore;
    
    @Column(precision = 19, scale = 2)
    private BigDecimal fromBullionAmountAfter;
    
    @Column(precision = 19, scale = 2)
    private BigDecimal toBullionAmountBefore;
    
    @Column(precision = 19, scale = 2)
    private BigDecimal toBullionAmountAfter;
    
    @Column(nullable = false)
    private Long userId;
    
    @Column(length = 500)
    private String description;

    @Column(length = 500)
    private String userComment;
    
    @Column(nullable = false, length = 20)
    private String status;
    
    private String errorMessage;
    
    /**
     * ID группы операций (для массовых операций)
     * Например, при переводе всего портфеля - все операции получают один batchId
     */
    private Long batchId;
    
    /**
     * ID родительской транзакции (для откатов/повторов)
     * При откате: parentTransactionId = id откатываемой операции
     * При повторе: parentTransactionId = id операции, которую повторяем
     */
    private Long parentTransactionId;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}