package ru.money.goldentaurusbank.www.backend.service.telegram;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.money.goldentaurusbank.www.backend.config.TelegramProperties;
import ru.money.goldentaurusbank.www.backend.model.dto.telegram.TelegramUpdate;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramPoller {

    private static final int LONG_POLL_SECONDS = 25;
    private static final long ERROR_PAUSE_MS = 15_000;

    private final TelegramProperties properties;
    private final TelegramClient telegramClient;
    private final TelegramUpdateHandler updateHandler;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;
    private long offset = 0;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.isEnabled()) {
            log.info("Телеграм-бот выключен: app.telegram.enabled = false");
            return;
        }

        if (properties.getBotToken().isBlank()) {
            log.warn("Телеграм-бот включён, но токен пуст: app.telegram.bot-token");
            return;
        }

        running.set(true);
        worker = new Thread(this::loop, "telegram-poller");
        worker.setDaemon(true);
        worker.start();
        log.info("Телеграм-бот запущен");
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (worker != null) {
            worker.interrupt();
        }
    }

    private void loop() {
        while (running.get()) {
            try {
                List<TelegramUpdate> updates = telegramClient.getUpdates(offset, LONG_POLL_SECONDS);
                for (TelegramUpdate update : updates) {
                    offset = update.updateId() + 1;
                    try {
                        updateHandler.handle(update);
                    } catch (Exception e) {
                        log.error("Не удалось обработать сообщение {}", update.updateId(), e);
                    }
                }
            } catch (Exception e) {
                if (!running.get()) {
                    return;
                }
                log.error("Телеграм недоступен, пауза перед повтором", e);
                sleep();
            }
        }
    }

    private void sleep() {
        try {
            Thread.sleep(ERROR_PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
