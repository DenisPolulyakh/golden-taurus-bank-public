--liquibase formatted sql

--changeset dpolulyakh:card_requisites_table
--comment: Реквизиты карт лежат одним зашифрованным блобом — ключ только у владельца, сервер содержимое не читает
CREATE TABLE IF NOT EXISTS taurus.card_requisites (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT    NOT NULL,
    payload    TEXT      NOT NULL,
    version    BIGINT    NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_card_requisites_user FOREIGN KEY (user_id) REFERENCES taurus.users(id) ON DELETE CASCADE,
    CONSTRAINT uk_card_requisites_user UNIQUE (user_id),
    CONSTRAINT chk_card_requisites_payload_size CHECK (length(payload) <= 1048576)
);

COMMENT ON COLUMN taurus.card_requisites.payload IS 'JSON-конверт: argon2id-соль, завёрнутый DEK и карты под AES-256-GCM; расшифровка только в браузере';

--rollback DROP TABLE IF EXISTS taurus.card_requisites;
