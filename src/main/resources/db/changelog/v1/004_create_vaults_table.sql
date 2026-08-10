--liquibase formatted sql

--changeset dpolulyakh:vaults_table
--comment: Хранилища с процентной ставкой и привязкой к банку
CREATE TABLE IF NOT EXISTS taurus.vaults (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100)  NOT NULL,
    interest_rate DECIMAL(5,2)  NOT NULL DEFAULT 0,
    description   VARCHAR(500),
    user_id       BIGINT        NOT NULL,
    vault_type    VARCHAR(30)   NOT NULL DEFAULT 'REGULAR',
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    bank_id       BIGINT,
    account_type  VARCHAR(30)   NOT NULL DEFAULT 'SAVINGS',
    close_date    DATE,

    CONSTRAINT fk_vaults_user FOREIGN KEY (user_id) REFERENCES taurus.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_vaults_bank FOREIGN KEY (bank_id) REFERENCES taurus.bank_dictionary(id) ON DELETE SET NULL
);

COMMENT ON COLUMN taurus.vaults.bank_id IS 'ID банка из справочника банков';
COMMENT ON COLUMN taurus.vaults.account_type IS 'Тип счета: SAVINGS - Накопительный, TERM - Срочный';
COMMENT ON COLUMN taurus.vaults.close_date IS 'Дата закрытия для срочных вкладов';

--changeset dpolulyakh:vaults_indexes
--comment: Индексы для хранилищ
CREATE INDEX IF NOT EXISTS idx_vaults_user_id ON taurus.vaults(user_id);
CREATE INDEX IF NOT EXISTS idx_vaults_name ON taurus.vaults(name);
CREATE INDEX IF NOT EXISTS idx_vaults_bank_id ON taurus.vaults(bank_id);

--rollback DROP TABLE IF EXISTS taurus.vaults;
