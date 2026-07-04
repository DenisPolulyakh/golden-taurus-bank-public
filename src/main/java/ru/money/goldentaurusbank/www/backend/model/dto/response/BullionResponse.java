package ru.money.goldentaurusbank.www.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BullionResponse {
    private Long id;
    private CategoryInfo category;
    private VaultInfo vault;
    private BigDecimal amount;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryInfo {
        private Long id;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VaultInfo {
        private Long id;
        private String name;
        private BigDecimal interestRate;
        private String accountType;
        private LocalDate closeDate;
        private Boolean allowedEdit;
        private Boolean allowedTransfer;
        private Boolean allowedIncome;
        private Boolean allowedExpense;
        private Boolean allowedDelete;
        private boolean allowedChangeAmount;

    }


}