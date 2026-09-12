package ru.money.goldentaurusbank.www.backend.service.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.telegram.TelegramUpdate;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramUpdateHandler {

    private static final String NOT_LINKED = """
            Этот чат не привязан к аккаунту.

            Откройте GoldenTaurusBank → Настройки → Телеграм, получите код и пришлите его сюда:
            /link ВАШКОД""";

    private static final String GREETING = """
            GoldenTaurusBank на связи.

            /summary — сводка по картам
            /package — аварийный пакет файлом
            /unlink — отвязать этот чат""";

    private final TelegramLinkService telegramLinkService;
    private final TelegramCommandService telegramCommandService;
    private final TelegramClient telegramClient;

    public void handle(TelegramUpdate update) {
        Long chatId = update.chatId();
        if (chatId == null) {
            return;
        }

        String text = update.text();

        if (text.startsWith("/link")) {
            link(chatId, text);
            return;
        }

        Optional<User> user = telegramLinkService.userByChat(chatId);
        if (user.isEmpty()) {
            telegramClient.sendMessage(chatId, NOT_LINKED);
            return;
        }

        if (text.startsWith("/unlink")) {
            telegramLinkService.unlinkChat(chatId);
            telegramClient.sendMessage(chatId, "Чат отвязан. Данные сюда больше не приходят");
            return;
        }

        if (text.startsWith("/start") || text.startsWith("/help")) {
            telegramClient.sendMessage(chatId, GREETING);
            return;
        }

        if (text.startsWith("/summary")) {
            telegramCommandService.sendSummary(user.get(), chatId);
            return;
        }

        if (text.startsWith("/package")) {
            telegramCommandService.sendEmergencyPackage(user.get(), chatId);
            return;
        }

        telegramClient.sendMessage(chatId, "Не знаю такой команды. /help — что я умею");
    }

    private void link(Long chatId, String text) {
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            telegramClient.sendMessage(chatId, "Пришлите код вместе с командой: /link ВАШКОД");
            return;
        }

        try {
            telegramLinkService.linkChat(parts[1], chatId);
            telegramClient.sendMessage(chatId, "Чат привязан. /help — что я умею");
        } catch (ApplicationException e) {
            telegramClient.sendMessage(chatId, e.getMessage());
        }
    }
}
