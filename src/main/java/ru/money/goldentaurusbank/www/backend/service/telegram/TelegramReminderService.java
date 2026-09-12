package ru.money.goldentaurusbank.www.backend.service.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.money.goldentaurusbank.www.backend.model.domain.CreditCard;
import ru.money.goldentaurusbank.www.backend.model.domain.TelegramLink;
import ru.money.goldentaurusbank.www.backend.model.domain.TelegramReminderSent;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.CreditCardOperation;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.ReminderKind;
import ru.money.goldentaurusbank.www.backend.repository.CreditCardHistoryRepository;
import ru.money.goldentaurusbank.www.backend.repository.CreditCardRepository;
import ru.money.goldentaurusbank.www.backend.repository.TelegramLinkRepository;
import ru.money.goldentaurusbank.www.backend.repository.TelegramReminderSentRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramReminderService {

    private final TelegramLinkRepository telegramLinkRepository;
    private final CreditCardRepository creditCardRepository;
    private final CreditCardHistoryRepository creditCardHistoryRepository;
    private final TelegramReminderSentRepository reminderSentRepository;
    private final TelegramClient telegramClient;
    private final TelegramCommandService telegramCommandService;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Tbilisi")
    public void daily() {
        run(LocalDate.now());
    }

    @Scheduled(cron = "0 30 9 1 * *", zone = "Asia/Tbilisi")
    @Transactional(readOnly = true)
    public int sendMonthlyPackages() {
        int sent = 0;

        for (TelegramLink link : telegramLinkRepository.findAllWithUser()) {
            try {
                telegramCommandService.sendEmergencyPackage(link.getUser(), link.getChatId());
                sent++;
            } catch (Exception e) {
                log.error("Не удалось отправить месячный пакет в чат {}", link.getChatId(), e);
            }
        }

        return sent;
    }

    @Transactional
    public void run(LocalDate today) {
        Map<User, List<TelegramLink>> byUser = new LinkedHashMap<>();
        telegramLinkRepository.findAllWithUser()
                .forEach(link -> byUser.computeIfAbsent(link.getUser(), user -> new ArrayList<>()).add(link));

        byUser.forEach((user, links) -> {
            List<Long> chats = links.stream().map(TelegramLink::getChatId).toList();
            List<CreditCard> cards = creditCardRepository.findByUserAndArchivedFalse(user);

            cards.stream()
                    .filter(card -> card.getDebt() != null && card.getDebt().signum() > 0)
                    .filter(card -> card.getGracePeriodDate() != null)
                    .forEach(card -> remind(card, today, chats));

            if (today.getDayOfMonth() == 1) {
                remindAboutMissingDates(cards, today, chats);
            }
        });
    }

    private void remind(CreditCard card, LocalDate today, List<Long> chats) {
        long days = ChronoUnit.DAYS.between(today, card.getGracePeriodDate());

        ReminderKind kind = switch ((int) days) {
            case 7 -> ReminderKind.BEFORE_7;
            case 2 -> ReminderKind.BEFORE_2;
            case 1 -> ReminderKind.BEFORE_1;
            case -1 -> hasRepaymentAfterDueDate(card) ? null : ReminderKind.OVERDUE;
            default -> null;
        };

        if (kind == null || alreadySent(card.getId(), kind, today)) {
            return;
        }

        String text = switch (kind) {
            case BEFORE_7 -> TelegramReminderMessages.before7(card);
            case BEFORE_2 -> TelegramReminderMessages.before2(card);
            case BEFORE_1 -> TelegramReminderMessages.before1(card);
            default -> TelegramReminderMessages.overdue(card);
        };

        send(chats, text);
        markSent(card.getId(), kind, today);
    }

    private void remindAboutMissingDates(List<CreditCard> cards, LocalDate today, List<Long> chats) {
        List<CreditCard> missing = cards.stream()
                .filter(card -> card.getGracePeriodDate() == null)
                .filter(card -> card.getDebt() != null && card.getDebt().signum() > 0)
                .toList();

        if (missing.isEmpty() || alreadySent(missing.get(0).getId(), ReminderKind.NO_DATE, today)) {
            return;
        }

        send(chats, TelegramReminderMessages.noDate(missing));
        markSent(missing.get(0).getId(), ReminderKind.NO_DATE, today);
    }

    private boolean hasRepaymentAfterDueDate(CreditCard card) {
        return creditCardHistoryRepository.existsByCreditCardIdAndOperationAndDateOperationAfter(
                card.getId(), CreditCardOperation.REPAY, card.getGracePeriodDate().atStartOfDay());
    }

    private boolean alreadySent(Long cardId, ReminderKind kind, LocalDate today) {
        return reminderSentRepository.existsByCreditCardIdAndKindAndSentOn(cardId, kind, today);
    }

    private void markSent(Long cardId, ReminderKind kind, LocalDate today) {
        reminderSentRepository.save(TelegramReminderSent.builder()
                .creditCardId(cardId)
                .kind(kind)
                .sentOn(today)
                .build());
    }

    private void send(List<Long> chats, String text) {
        chats.forEach(chatId -> {
            try {
                telegramClient.sendMessage(chatId, text);
            } catch (Exception e) {
                log.error("Не удалось отправить напоминание в чат {}", chatId, e);
            }
        });
    }
}
