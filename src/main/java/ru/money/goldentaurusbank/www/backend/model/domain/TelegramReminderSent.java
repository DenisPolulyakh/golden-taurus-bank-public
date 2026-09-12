package ru.money.goldentaurusbank.www.backend.model.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.ReminderKind;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "telegram_reminders_sent", schema = "taurus")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramReminderSent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credit_card_id", nullable = false)
    private Long creditCardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReminderKind kind;

    @Column(name = "sent_on", nullable = false)
    private LocalDate sentOn;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
