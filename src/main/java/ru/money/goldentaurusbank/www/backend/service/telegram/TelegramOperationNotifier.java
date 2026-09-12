package ru.money.goldentaurusbank.www.backend.service.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.money.goldentaurusbank.www.backend.model.domain.TelegramLink;
import ru.money.goldentaurusbank.www.backend.model.dto.telegram.CreditCardOperationEvent;
import ru.money.goldentaurusbank.www.backend.repository.CreditCardRepository;
import ru.money.goldentaurusbank.www.backend.repository.TelegramLinkRepository;

import java.math.BigDecimal;
import java.util.List;

import static ru.money.goldentaurusbank.www.backend.service.telegram.TelegramFormat.amount;
import static ru.money.goldentaurusbank.www.backend.service.telegram.TelegramFormat.maskedCard;
import static ru.money.goldentaurusbank.www.backend.service.telegram.TelegramFormat.signedAmount;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramOperationNotifier {

    private final TelegramLinkRepository telegramLinkRepository;
    private final CreditCardRepository creditCardRepository;
    private final TelegramClient telegramClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOperation(CreditCardOperationEvent event) {
        List<TelegramLink> links = telegramLinkRepository.findByUserId(event.userId());
        if (links.isEmpty()) {
            return;
        }

        String text = build(event);
        links.forEach(link -> {
            try {
                telegramClient.sendMessage(link.getChatId(), text);
            } catch (Exception e) {
                log.error("Не удалось отправить уведомление в чат {}", link.getChatId(), e);
            }
        });
    }

    public String build(CreditCardOperationEvent event) {
        boolean increases = event.operation().increasesDebt();
        BigDecimal signed = increases ? event.amount() : event.amount().negate();

        StringBuilder text = new StringBuilder();
        if (event.rollback()) {
            text.append("Откат · ");
        }
        text.append(increases ? "Списание" : "Погашение");
        text.append(" · ").append(maskedCard(event.cardName(), event.last4())).append('\n');
        text.append(signedAmount(signed)).append("\n\n");
        text.append("Долг по карте: %s (было %s)\n"
                .formatted(amount(event.debtAfter()), amount(event.debtBefore())));

        if (increases && event.cardLimit() != null) {
            text.append("Остаток лимита: %s\n"
                    .formatted(amount(event.cardLimit().subtract(event.debtAfter()))));
        }

        BigDecimal totalDebt = creditCardRepository.getTotalDebtByUserId(event.userId());
        text.append("Общий долг: %s".formatted(amount(totalDebt)));

        return text.toString();
    }
}
