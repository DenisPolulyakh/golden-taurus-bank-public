package ru.money.goldentaurusbank.www.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.BullionType;

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
    private List<BullionNameBullion> bullionNameBullionList;
    private Integer countVaults;
    private Integer countBullions;


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BullionNameBullion {
        private Long bullionNameId;
        private String bullionNameTitle;
        private String bullionNameColor;
        private BigDecimal bullionNameAmount;
        private BigDecimal bullionNameAverageRate;
        private List<VaultInfo> vaults;
        // Флаги сгруппированной карточки: операция доступна, если её разрешает
        // хоть одно хранилище наименования. Считает бэк (BullionService),
        // фронт только гасит по ним кнопки.
        @Builder.Default
        private Boolean allowedIncome = true;
        @Builder.Default
        private Boolean allowedExpense = true;
        @Builder.Default
        private Boolean allowedTransfer = true;



        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class VaultInfo {
            private Long id;
            // Id слитка в этом хранилище: перевод адресуется слитку, а не хранилищу
            private Long bullionId;
            private String name;
            private BigDecimal amount;
            private String accountType;
            private LocalDate closeDate;
            // Дебетовый или кредитный — карточка рисует по нему печать
            private BullionType bullionType;
            // Накопитель карты: по этим полям сгруппированная карточка решает,
            // показывать ли кнопку «Погашение», и что писать в выпадающем списке
            private Long creditCardId;
            private String creditCardMasked;
            private BigDecimal creditCardDebt;
            // Без @Builder.Default билдер игнорирует "= true", и поле уходит
            // во фронт как null. JSON-дефолт (allowedIncome = true) при
            // деструктуризации от null не спасает — он ловит только undefined.
            @Builder.Default
            private Boolean allowedIncome = true;
            @Builder.Default
            private Boolean allowedExpense = true;
            @Builder.Default
            private Boolean allowedTransfer = true;
            // Правка суммы - это внесение или снятие в зависимости от направления,
            // поэтому флаг производный, своей галочки у него нет
            @Builder.Default
            private Boolean allowedChangeAmount = true;
            // Производные флаги перевода: перевести из хранилища = снять оттуда,
            // перевести в хранилище = внести туда. Правило домена, фронт его не считает.
            @Builder.Default
            private Boolean allowedTransferOut = true;
            @Builder.Default
            private Boolean allowedTransferIn = true;
        }

    }




}