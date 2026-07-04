package ru.money.goldentaurusbank.www.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.AccountType;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.VaultType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VaultResponse {
    private Long id;
    private String name;
    private BigDecimal interestRate;
    private String description;
    private Long bankId;
    private String bankName;
    private VaultType vaultType;
    private AccountType accountType;
    private LocalDate closeDate;
    private BigDecimal totalAmount;
    private Integer categoriesCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String displayName;
    private boolean allowedIncome = true;
    private boolean allowedExpense = true;
    private boolean allowedDelete = true;
    private boolean allowedTransfer = true;
    private boolean allowedEdit = true;
    private boolean allowedChangeAmount = true;

    public String getDisplayName() {
        if (bankName != null) {
            return bankName + "_" + name;
        }
        return name;
    }
}