--liquibase formatted sql

--changeset dpolulyakh:transactions_income_type
--comment: Тип дохода при пополнении слитка. NULL — без классификации, в статистику доходов не попадает. Возможен только у пополнения (сумма зашла на слиток, без исходящей ноги, не стартовый остаток)
ALTER TABLE taurus.transactions ADD COLUMN IF NOT EXISTS income_type VARCHAR(30);

ALTER TABLE taurus.transactions
    ADD CONSTRAINT chk_transactions_income_only_deposit
    CHECK (income_type IS NULL OR (
        target_bullion_id IS NOT NULL
        AND source_bullion_id IS NULL
        AND NOT opening_balance
    ));

CREATE INDEX IF NOT EXISTS idx_transactions_user_income_date
    ON taurus.transactions(user_id, date_operation)
    WHERE income_type IS NOT NULL;

--rollback ALTER TABLE taurus.transactions DROP CONSTRAINT IF EXISTS chk_transactions_income_only_deposit;
--rollback DROP INDEX IF EXISTS taurus.idx_transactions_user_income_date;
--rollback ALTER TABLE taurus.transactions DROP COLUMN IF EXISTS income_type;
