--liquibase formatted sql

--changeset dpolulyakh:telegram_links_table
--comment: Привязка телеграм-чата к аккаунту. Чат принадлежит одному пользователю, у пользователя чатов может быть несколько
CREATE TABLE IF NOT EXISTS taurus.telegram_links (
    id        BIGSERIAL PRIMARY KEY,
    user_id   BIGINT    NOT NULL,
    chat_id   BIGINT    NOT NULL,
    linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_telegram_links_user FOREIGN KEY (user_id) REFERENCES taurus.users(id) ON DELETE CASCADE,
    CONSTRAINT uk_telegram_links_chat UNIQUE (chat_id)
);

CREATE INDEX IF NOT EXISTS idx_telegram_links_user ON taurus.telegram_links(user_id);

--changeset dpolulyakh:telegram_link_codes_table
--comment: Одноразовые коды привязки: живут 15 минут, сгорают при использовании
CREATE TABLE IF NOT EXISTS taurus.telegram_link_codes (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    code       VARCHAR(16) NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_telegram_link_codes_user FOREIGN KEY (user_id) REFERENCES taurus.users(id) ON DELETE CASCADE,
    CONSTRAINT uk_telegram_link_codes_code UNIQUE (code)
);

--rollback DROP TABLE IF EXISTS taurus.telegram_link_codes;
--rollback DROP TABLE IF EXISTS taurus.telegram_links;
