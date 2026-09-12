--liquibase formatted sql

--changeset dpolulyakh:telegram_reminders_sent
--comment: Что уже отправлено: без этой таблицы перезапуск контейнера утром повторит все напоминания
CREATE TABLE IF NOT EXISTS taurus.telegram_reminders_sent (
    id             BIGSERIAL   PRIMARY KEY,
    credit_card_id BIGINT      NOT NULL,
    kind           VARCHAR(20) NOT NULL,
    sent_on        DATE        NOT NULL,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_telegram_reminders_card FOREIGN KEY (credit_card_id) REFERENCES taurus.credit_cards(id) ON DELETE CASCADE,
    CONSTRAINT uk_telegram_reminders UNIQUE (credit_card_id, kind, sent_on)
);

--rollback DROP TABLE IF EXISTS taurus.telegram_reminders_sent;
