--liquibase formatted sql

--changeset dpolulyakh:bullions_bullion_type
--comment: Тип слитка: дебетовый (свои накопления) или кредитный (заёмные). Строка, а не enum БД — как account_type у хранилищ
ALTER TABLE taurus.bullions
    ADD COLUMN IF NOT EXISTS bullion_type VARCHAR(20) NOT NULL DEFAULT 'DEBIT';

COMMENT ON COLUMN taurus.bullions.bullion_type IS 'DEBIT — свои накопления, CREDIT — заёмные средства';

--rollback ALTER TABLE taurus.bullions DROP COLUMN IF EXISTS bullion_type;
