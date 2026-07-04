package ru.money.goldentaurusbank.www.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupedBullionResponse {
    private BigDecimal totalAmount;
    private BigDecimal averageRate;
    private List<CategoryBullion> categoryBullionList;
    private Integer countVaults;
    private Integer countBullions;


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryBullion {
        private Long categoryId;
        private String categoryName;
        private String categoryColor;
        private BigDecimal categoryAmount;
        private BigDecimal categoryAverageRate;
        private List<VaultInfo> vaults;



        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class VaultInfo {
            private Long id;
            private String name;
            private BigDecimal amount;
            private String accountType;
            private LocalDate closeDate;
            private Boolean allowedIncome = true;
            private Boolean allowedExpense = true;
            private Boolean allowedDelete = true;
            private Boolean allowedTransfer = true;
            private Boolean allowedEdit = true;
            private Boolean allowedChangeAmount= true;
        }

    }




}