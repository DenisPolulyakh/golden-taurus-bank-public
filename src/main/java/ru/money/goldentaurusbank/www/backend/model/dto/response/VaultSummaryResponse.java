package ru.money.goldentaurusbank.www.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.BullionType;

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
    private Integer bullionNamesCount;
    private List<BullionByBullionNameResponse> bullions;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BullionByBullionNameResponse {
        private Long id;
        private Long bullionNameId;
        private String bullionNameTitle;
        private BigDecimal amount;
        private String description;
        // Нужен форме редактирования слитка: подставить текущий тип
        private BullionType bullionType;

        /** Бюджетный слиток: в формах операций по нему появляется галочка «Трата бюджета» */
        private boolean budget;
        // Слиток служит накопителем карты: отсюда у него кнопка «Погашение»
        private Long creditCardId;
        private String creditCardMasked;
        private BigDecimal creditCardDebt;
    }
}