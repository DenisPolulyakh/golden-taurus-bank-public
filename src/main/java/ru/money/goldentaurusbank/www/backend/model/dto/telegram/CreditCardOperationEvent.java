package ru.money.goldentaurusbank.www.backend.model.dto.telegram;

import ru.money.goldentaurusbank.www.backend.model.dto.enums.CreditCardOperation;

import java.math.BigDecimal;

public record CreditCardOperationEvent(
        Long userId,
        String cardName,
        String last4,
        CreditCardOperation operation,
        BigDecimal amount,
        BigDecimal debtBefore,
        BigDecimal debtAfter,
        BigDecimal cardLimit,
        boolean rollback
) {
}
