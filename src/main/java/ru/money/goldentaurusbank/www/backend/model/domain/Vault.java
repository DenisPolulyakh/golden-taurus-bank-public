package ru.money.goldentaurusbank.www.backend.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.AccountType;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.VaultType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "vaults", schema = "taurus")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vault {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "interest_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal interestRate = BigDecimal.ZERO;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Архивные слитки живут только ради истории — в состав хранилища они не входят.
    // Удаление хранилища их не трогает: на уровне БД vault_id просто обнуляется.
    @OneToMany(mappedBy = "vault", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @SQLRestriction("NOT archived")
    @Builder.Default
    private List<Bullion> bullions = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "vault_type", nullable = false)
    @Builder.Default
    private VaultType vaultType = VaultType.REGULAR;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    private BankDictionary bank;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "close_date")
    private LocalDate closeDate;

    public BigDecimal getTotalAmount() {
        if (bullions == null || bullions.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return bullions.stream()
                .map(Bullion::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Integer getBullionNamesCount() {
        if (bullions == null || bullions.isEmpty()) {
            return 0;
        }
        return (int) bullions.stream()
                .map(Bullion::getBullionName)
                .filter(Objects::nonNull)
                .map(BullionName::getId)
                .distinct()
                .count();
    }
}