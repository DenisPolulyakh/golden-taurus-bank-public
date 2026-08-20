package ru.money.goldentaurusbank.www.backend.model.mapper;

import org.springframework.stereotype.Component;
import ru.money.goldentaurusbank.www.backend.model.domain.Bullion;
import ru.money.goldentaurusbank.www.backend.model.domain.CreditCard;
import ru.money.goldentaurusbank.www.backend.model.domain.CreditCardHistory;
import ru.money.goldentaurusbank.www.backend.model.domain.Vault;
import ru.money.goldentaurusbank.www.backend.model.dto.response.CreditCardHistoryResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.CreditCardResponse;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Собран руками, а не MapStruct: почти все поля ответа — производные
 * (маска, остаток, дисбаланс, счётчик дней), переносить один в один нечего.
 */
@Component
public class CreditCardMapper {

    public static final String MASK_PREFIX = "•••• ";

    public CreditCardResponse toResponse(CreditCard card) {
        return CreditCardResponse.builder()
                .id(card.getId())
                .name(card.getName())
                .maskedNumber(mask(card.getLast4()))
                .last4(card.getLast4())
                .gracePeriodDate(card.getGracePeriodDate())
                .graceDaysLeft(graceDaysLeft(card.getGracePeriodDate()))
                .limit(card.getCardLimit())
                .debt(card.getDebt())
                .remainder(card.getRemainder())
                .accumulatedAmount(card.getAccumulatedAmount())
                .imbalance(card.getImbalance())
                .accumulators(toAccumulators(card.getAccumulators()))
                .createdAt(card.getCreatedAt())
                .updatedAt(card.getUpdatedAt())
                .build();
    }

    public List<CreditCardResponse> toResponseList(List<CreditCard> cards) {
        return cards.stream().map(this::toResponse).toList();
    }

    public List<CreditCardResponse.AccumulatorInfo> toAccumulators(List<Bullion> bullions) {
        if (bullions == null) {
            return List.of();
        }
        return bullions.stream().map(this::toAccumulator).toList();
    }

    public CreditCardResponse.AccumulatorInfo toAccumulator(Bullion bullion) {
        Vault vault = bullion.getVault();
        return CreditCardResponse.AccumulatorInfo.builder()
                .bullionId(bullion.getId())
                .bullionNameTitle(bullion.getBullionName() == null ? null : bullion.getBullionName().getTitle())
                .bullionNameColor(bullion.getBullionName() == null ? null : bullion.getBullionName().getColor())
                .vaultId(vault == null ? null : vault.getId())
                .vaultName(vault == null ? null : vault.getName())
                .amount(bullion.getAmount())
                .build();
    }

    public CreditCardHistoryResponse toHistoryResponse(CreditCardHistory history, Long reversedById, String cardName) {
        return CreditCardHistoryResponse.builder()
                .id(history.getId())
                .operation(history.getOperation())
                .description(describe(history, cardName))
                .amount(history.getAmount())
                .signedAmount(history.getSignedAmount())
                .debtAfter(history.getDebtAfter())
                .dateOperation(history.getDateOperation())
                .createdAt(history.getCreatedAt())
                .comment(history.getComment())
                .canRollback(reversedById == null && history.getReversalOfId() == null)
                .reversalOfId(history.getReversalOfId())
                .reversedById(reversedById)
                .bullionTransactionId(history.getBullionTransactionId())
                .build();
    }

    public String mask(String last4) {
        return MASK_PREFIX + last4;
    }

    /** {@code null} — периода нет; отрицательное — просрочен. */
    public Integer graceDaysLeft(LocalDate gracePeriodDate) {
        if (gracePeriodDate == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), gracePeriodDate);
    }

    private String describe(CreditCardHistory history, String cardName) {
        String card = cardName == null ? "карты" : "карты " + cardName;
        return switch (history.getOperation()) {
            case SPEND -> "Списание " + history.getAmount() + " с " + card;
            case REPAY -> history.getBullionTransactionId() == null
                    ? "Погашение " + history.getAmount() + " по " + card
                    : "Погашение " + history.getAmount() + " по " + card + " из накопителя";
            case OPENING_DEBT -> "Начальная задолженность " + history.getAmount() + " по " + card;
        };
    }
}
