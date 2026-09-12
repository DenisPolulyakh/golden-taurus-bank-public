package ru.money.goldentaurusbank.www.backend.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.telegram.TelegramUpdate;
import ru.money.goldentaurusbank.www.backend.service.telegram.TelegramClient;
import ru.money.goldentaurusbank.www.backend.service.telegram.TelegramCommandService;
import ru.money.goldentaurusbank.www.backend.service.telegram.TelegramLinkService;
import ru.money.goldentaurusbank.www.backend.service.telegram.TelegramUpdateHandler;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Обработчик сообщений телеграм-бота")
class TelegramUpdateHandlerTest {

    private static final Long CHAT = 100L;

    private TelegramClient telegramClient;
    private TelegramLinkService linkService;
    private TelegramCommandService commandService;
    private TelegramUpdateHandler handler;

    @BeforeEach
    void setUp() {
        telegramClient = mock(TelegramClient.class);
        linkService = mock(TelegramLinkService.class);
        commandService = mock(TelegramCommandService.class);
        handler = new TelegramUpdateHandler(linkService, commandService, telegramClient);
    }

    @Test
    @DisplayName("Непривязанный чат получает инструкцию и ничего больше")
    void unlinkedChatGetsInstruction() {
        when(linkService.userByChat(CHAT)).thenReturn(Optional.empty());

        handler.handle(update("/summary"));

        assertThat(captureMessage()).contains("/link");
        verify(telegramClient, never()).sendDocument(any(), anyString(), any(), anyString());
        verifyNoMoreInteractions(telegramClient);
    }

    @Test
    @DisplayName("Команда /link с верным кодом привязывает чат")
    void linkWithValidCode() {
        when(linkService.linkChat("ABCD2345", CHAT)).thenReturn(new User());

        handler.handle(update("/link ABCD2345"));

        verify(linkService).linkChat("ABCD2345", CHAT);
        assertThat(captureMessage()).contains("привязан");
    }

    @Test
    @DisplayName("Команда /link с негодным кодом объясняет причину")
    void linkWithInvalidCode() {
        when(linkService.linkChat(anyString(), eq(CHAT)))
                .thenThrow(new ApplicationException(4041, "Код не подходит или уже использован"));

        handler.handle(update("/link ZZZZZZZZ"));

        assertThat(captureMessage()).contains("Код не подходит");
    }

    @Test
    @DisplayName("Команда /link без кода подсказывает формат")
    void linkWithoutCode() {
        handler.handle(update("/link"));

        assertThat(captureMessage()).contains("/link ВАШКОД");
        verify(linkService, never()).linkChat(anyString(), any());
    }

    @Test
    @DisplayName("В привязанном чате /start возвращает список команд")
    void startInLinkedChat() {
        when(linkService.userByChat(CHAT)).thenReturn(Optional.of(new User()));

        handler.handle(update("/start"));

        assertThat(captureMessage()).contains("/summary", "/package");
    }

    @Test
    @DisplayName("Команда /unlink отвязывает чат")
    void unlinkChat() {
        when(linkService.userByChat(CHAT)).thenReturn(Optional.of(new User()));

        handler.handle(update("/unlink"));

        verify(linkService).unlinkChat(CHAT);
        assertThat(captureMessage()).contains("отвязан");
    }

    @Test
    @DisplayName("Неизвестная команда получает подсказку про /help")
    void unknownCommand() {
        when(linkService.userByChat(CHAT)).thenReturn(Optional.of(new User()));

        handler.handle(update("привет"));

        assertThat(captureMessage()).contains("/help");
    }

    @Test
    @DisplayName("Команда /summary отправляет сводку")
    void summaryCommand() {
        User user = new User();
        when(linkService.userByChat(CHAT)).thenReturn(Optional.of(user));

        handler.handle(update("/summary"));

        verify(commandService).sendSummary(user, CHAT);
        verifyNoMoreInteractions(telegramClient);
    }

    @Test
    @DisplayName("Команда /package отправляет пакет файлом")
    void packageCommand() {
        User user = new User();
        when(linkService.userByChat(CHAT)).thenReturn(Optional.of(user));

        handler.handle(update("/package"));

        verify(commandService).sendEmergencyPackage(user, CHAT);
        verifyNoMoreInteractions(telegramClient);
    }

    @Test
    @DisplayName("Апдейт без чата молча игнорируется")
    void updateWithoutChat() {
        handler.handle(new TelegramUpdate(1L, null));

        verifyNoMoreInteractions(telegramClient);
        verifyNoMoreInteractions(linkService);
    }

    private String captureMessage() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(eq(CHAT), text.capture());

        return text.getValue();
    }

    private TelegramUpdate update(String text) {
        return new TelegramUpdate(1L, new TelegramUpdate.TelegramMessage(
                new TelegramUpdate.TelegramChat(CHAT), text));
    }
}
