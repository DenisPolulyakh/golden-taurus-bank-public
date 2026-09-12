package ru.money.goldentaurusbank.www.backend.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.money.goldentaurusbank.www.backend.model.domain.TelegramLink;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.CreditCardOperation;
import ru.money.goldentaurusbank.www.backend.model.dto.telegram.CreditCardOperationEvent;
import ru.money.goldentaurusbank.www.backend.repository.CreditCardRepository;
import ru.money.goldentaurusbank.www.backend.repository.TelegramLinkRepository;
import ru.money.goldentaurusbank.www.backend.service.telegram.TelegramClient;
import ru.money.goldentaurusbank.www.backend.service.telegram.TelegramOperationNotifier;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Уведомления об операциях по картам")
class TelegramOperationNotifierTest {

    private static final Long USER_ID = 7L;
    private static final Long CHAT = 100L;

    private TelegramLinkRepository linkRepository;
    private CreditCardRepository cardRepository;
    private TelegramClient telegramClient;
    private TelegramOperationNotifier notifier;

    @BeforeEach
    void setUp() {
        linkRepository = mock(TelegramLinkRepository.class);
        cardRepository = mock(CreditCardRepository.class);
        telegramClient = mock(TelegramClient.class);
        notifier = new TelegramOperationNotifier(linkRepository, cardRepository, telegramClient);

        when(cardRepository.getTotalDebtByUserId(USER_ID)).thenReturn(new BigDecimal("1000000"));
    }

    @Test
    @DisplayName("Погашение показывает минус, долг по карте и то, что было")
    void repayMessage() {
        String text = notifier.build(event(CreditCardOperation.REPAY, "100000", "300000", "200000", false));

        assertThat(text).contains("Погашение · Платинум ****4567");
        assertThat(text).contains("−100 000 ₽");
        assertThat(text).contains("Долг по карте: 200 000 ₽ (было 300 000 ₽)");
        assertThat(text).contains("Общий долг: 1 000 000 ₽");
        assertThat(text).doesNotContain("Остаток лимита");
    }

    @Test
    @DisplayName("Списание показывает плюс и остаток лимита")
    void spendMessage() {
        String text = notifier.build(event(CreditCardOperation.SPEND, "20000", "100000", "120000", false));

        assertThat(text).contains("Списание · Платинум ****4567");
        assertThat(text).contains("+20 000 ₽");
        assertThat(text).contains("Остаток лимита: 380 000 ₽");
    }

    @Test
    @DisplayName("Откат операции помечается в первой строке")
    void rollbackIsMarked() {
        String text = notifier.build(event(CreditCardOperation.SPEND, "20000", "100000", "120000", true));

        assertThat(text).startsWith("Откат · Списание");
    }

    @Test
    @DisplayName("Нет привязанных чатов — ничего не отправляется")
    void noChatsNoMessages() {
        when(linkRepository.findByUserId(USER_ID)).thenReturn(List.of());

        notifier.onOperation(event(CreditCardOperation.REPAY, "100000", "300000", "200000", false));

        verify(telegramClient, never()).sendMessage(anyLong(), anyString());
    }

    @Test
    @DisplayName("Недоступный телеграм не ломает обработку события")
    void telegramFailureIsSwallowed() {
        when(linkRepository.findByUserId(USER_ID)).thenReturn(List.of(link()));
        doThrow(new RuntimeException("telegram down"))
                .when(telegramClient).sendMessage(any(), anyString());

        assertThatCode(() -> notifier.onOperation(
                event(CreditCardOperation.REPAY, "100000", "300000", "200000", false)))
                .doesNotThrowAnyException();
    }

    private TelegramLink link() {
        return TelegramLink.builder().id(1L).chatId(CHAT).build();
    }

    private CreditCardOperationEvent event(CreditCardOperation operation, String amount,
                                           String debtBefore, String debtAfter, boolean rollback) {
        return new CreditCardOperationEvent(
                USER_ID, "Платинум", "4567", operation,
                new BigDecimal(amount), new BigDecimal(debtBefore), new BigDecimal(debtAfter),
                new BigDecimal("500000"), rollback);
    }
}
