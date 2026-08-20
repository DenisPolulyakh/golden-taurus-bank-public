package ru.money.goldentaurusbank.www.backend.model.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "credit_cards", schema = "taurus")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * В коде номер открытый, в БД — шифртекст: за это отвечает конвертер.
     * Наружу не отдаётся никогда, в API уходит только маска.
     */
    @Convert(converter = CardNumberConverter.class)
    @Column(name = "card_number_enc", nullable = false, length = 512)
    private String cardNumber;

    /** Открытым: шифр недетерминированный, искать и маскировать больше нечем. */
    @Column(name = "card_last4", nullable = false, length = 4)
    private String last4;

    @Column(name = "grace_period_date")
    private LocalDate gracePeriodDate;

    /** {@code limit} — зарезервированное слово Postgres, отсюда имя колонки. */
    @Column(name = "card_limit", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal cardLimit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal debt = BigDecimal.ZERO;

    /**
     * Карта с историей не удаляется физически — на неё ссылается
     * {@code credit_card_history}. Архивная пропадает из списков и из общего долга.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean archived = false;

    /**
     * Накопитель: кредитные слитки, в которых копятся деньги на погашение.
     * Архивный слиток в накопитель не входит — как и в состав хранилища.
     */
    @OneToMany(mappedBy = "creditCard", fetch = FetchType.LAZY)
    @SQLRestriction("NOT archived")
    @Builder.Default
    private List<Bullion> accumulators = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Сколько ещё можно потратить. */
    @Transient
    public BigDecimal getRemainder() {
        return cardLimit.subtract(debt);
    }

    /** Сколько уже накоплено на погашение. */
    @Transient
    public BigDecimal getAccumulatedAmount() {
        if (accumulators == null || accumulators.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return accumulators.stream()
                .map(Bullion::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Накоплено минус долг: минус — на погашение не хватает, плюс — хватает.
     * Знак выбран так, чтобы зелёный на экране означал «всё в порядке».
     */
    @Transient
    public BigDecimal getImbalance() {
        return getAccumulatedAmount().subtract(debt);
    }
}
