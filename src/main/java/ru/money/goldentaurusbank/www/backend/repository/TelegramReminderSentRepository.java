package ru.money.goldentaurusbank.www.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.money.goldentaurusbank.www.backend.model.domain.TelegramReminderSent;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.ReminderKind;

import java.time.LocalDate;

public interface TelegramReminderSentRepository extends JpaRepository<TelegramReminderSent, Long> {

    boolean existsByCreditCardIdAndKindAndSentOn(Long creditCardId, ReminderKind kind, LocalDate sentOn);
}
