package ru.money.goldentaurusbank.www.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VaultSummaryResponse {
    private Long vaultId;
    private String vaultName;
    private BigDecimal totalAmount;
    private BigDecimal interestRate;
    private Integer categoriesCount;
    private List<BullionByCategoryResponse> bullions;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BullionByCategoryResponse {
        private Long id;
        private Long categoryId;
        private String categoryName;
        private BigDecimal amount;
        private String description;
    }
}