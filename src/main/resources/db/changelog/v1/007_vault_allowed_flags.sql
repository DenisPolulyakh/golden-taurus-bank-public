--liquibase formatted sql

--changeset dpolulyakh:vaults_allowed_flags
--comment: Галочки разрешённых операций: что можно делать со слитками этого хранилища
ALTER TABLE taurus.vaults
    ADD COLUMN IF NOT EXISTS allowed_income   BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS allowed_expense  BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS allowed_transfer BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN taurus.vaults.allowed_income IS 'Разрешено вносить средства в хранилище';
COMMENT ON COLUMN taurus.vaults.allowed_expense IS 'Разрешено снимать средства из хранилища';
COMMENT ON COLUMN taurus.vaults.allowed_transfer IS 'Разрешено переводить между слитками';

--rollback ALTER TABLE taurus.vaults DROP COLUMN IF EXISTS allowed_income, DROP COLUMN IF EXISTS allowed_expense, DROP COLUMN IF EXISTS allowed_transfer;
