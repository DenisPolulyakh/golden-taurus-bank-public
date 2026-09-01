package ru.money.goldentaurusbank.www.backend.model.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.BullionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "bullions", schema = "taurus")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bullion {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bullion_name_id", nullable = false)
    private BullionName bullionName;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vault_id")
    private Vault vault;
    
    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;
    
    @Column(length = 500)
    private String description;

    /**
     * Дебетовый или кредитный. На суммы, проценты и операции пока не влияет —
     * заготовка под учёт заёмных средств.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "bullion_type", nullable = false, length = 20)
    @Builder.Default
    private BullionType bullionType = BullionType.DEBIT;

    /**
     * Кредитная карта, накопителем которой служит слиток. Только для кредитных
     * слитков: в накопителе копятся деньги на погашение долга этой карты.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_card_id")
    private CreditCard creditCard;

    /**
     * Слиток с историей нельзя удалить физически — на него ссылаются транзакции.
     * Архивный слиток не попадает в списки и в сумму накоплений.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean archived = false;


    /**
     * Слиток текущих расходов, по которому строится отчёт «Бюджет на месяц».
     * Активный такой слиток у пользователя один — держит частичный уникальный
     * индекс uq_bullions_budget_per_user, а не проверка в коде: два бюджетных
     * слитка это баг, а не ошибка пользователя.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean budget = false;

    /**
     * Слиток, с которого бюджет финансируется кнопкой «Профинансировать».
     * На арифметику отчёта не влияет — только подставляется в форму перевода.
     */
    @Column(name = "budget_source", nullable = false)
    @Builder.Default
    private boolean budgetSource = false;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}