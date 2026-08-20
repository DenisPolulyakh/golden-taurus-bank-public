--liquibase formatted sql

--changeset dpolulyakh:credit_cards_table
--comment: Кредитная карта. Номер лежит только шифртекстом, открытым хранятся последние 4 цифры — по ним идёт поиск и строится маска
CREATE TABLE IF NOT EXISTS taurus.credit_cards (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT        NOT NULL,
    name              VARCHAR(100)  NOT NULL,
    card_number_enc   VARCHAR(512)  NOT NULL,
    card_last4        VARCHAR(4)    NOT NULL,
    grace_period_date DATE,
    card_limit        DECIMAL(19,2) NOT NULL DEFAULT 0 CHECK (card_limit >= 0),
    debt              DECIMAL(19,2) NOT NULL DEFAULT 0 CHECK (debt >= 0),
    archived          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_credit_cards_user FOREIGN KEY (user_id) REFERENCES taurus.users(id) ON DELETE CASCADE,
    CONSTRAINT chk_credit_cards_debt_within_limit CHECK (debt <= card_limit)
);

COMMENT ON COLUMN taurus.credit_cards.card_limit IS 'limit — зарезервированное слово Postgres, отсюда card_limit';
COMMENT ON COLUMN taurus.credit_cards.card_number_enc IS 'AES-256-GCM, ключ из CARD_SECRET/CARD_SALT; шифр недетерминированный, искать по нему нельзя';

--changeset dpolulyakh:credit_cards_indexes
--comment: Активные карты пользователя и уникальность названия среди них
CREATE INDEX IF NOT EXISTS idx_credit_cards_user_active ON taurus.credit_cards(user_id) WHERE NOT archived;
CREATE UNIQUE INDEX IF NOT EXISTS uk_credit_cards_user_name ON taurus.credit_cards(user_id, lower(name)) WHERE NOT archived;

--changeset dpolulyakh:bullions_credit_card
--comment: Накопитель карты — кредитные слитки, привязанные к ней. RESTRICT: карта с накопителем не удаляется физически, она архивируется
ALTER TABLE taurus.bullions ADD COLUMN IF NOT EXISTS credit_card_id BIGINT;
ALTER TABLE taurus.bullions ADD CONSTRAINT fk_bullions_credit_card
    FOREIGN KEY (credit_card_id) REFERENCES taurus.credit_cards(id) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_bullions_credit_card_id ON taurus.bullions(credit_card_id);

--rollback ALTER TABLE taurus.bullions DROP CONSTRAINT IF EXISTS fk_bullions_credit_card;
--rollback ALTER TABLE taurus.bullions DROP COLUMN IF EXISTS credit_card_id;
--rollback DROP TABLE IF EXISTS taurus.credit_cards;
