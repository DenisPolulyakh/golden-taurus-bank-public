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
    private Integer bullionNamesCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String displayName;
    // @Builder.Default обязателен: без него билдер молча игнорирует "= true"
    // и поле уезжает как false. Здесь значения всё равно перетирает
    // VaultMapper.enrichVaultResponse, но объявление должно быть честным.
    @Builder.Default
    private boolean allowedIncome = true;
    @Builder.Default
    private boolean allowedExpense = true;
    @Builder.Default
    private boolean allowedDelete = true;
    @Builder.Default
    private boolean allowedTransfer = true;
    @Builder.Default
    private boolean allowedEdit = true;
    @Builder.Default
    private boolean allowedChangeAmount = true;
    // Перевести из хранилища = снять оттуда, перевести в него = внести туда.
    // Заполняет VaultMapper.enrichVaultResponse
    @Builder.Default
    private boolean allowedTransferOut = true;
    @Builder.Default
    private boolean allowedTransferIn = true;

    private Settings settings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Settings {
        private boolean allowedIncome;
        private boolean allowedExpense;
        private boolean allowedTransfer;
    }


    public String getDisplayName() {
        if (bankName != null) {
            return bankName + "_" + name;
        }
        return name;
    }
}