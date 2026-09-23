--liquibase formatted sql

--changeset dpolulyakh:budget_months_close_snapshot
--comment: Закрытие месяца фиксируется снимком (план/финансирование/потрачено/остатки на момент закрытия), а не только переводом остатка. planned_amount перестаёт быть обязательным — план мог быть не задан
ALTER TABLE taurus.budget_months ALTER COLUMN planned_amount DROP NOT NULL;
ALTER TABLE taurus.budget_months ALTER COLUMN planned_amount DROP DEFAULT;

ALTER TABLE taurus.budget_months ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE taurus.budget_months ADD COLUMN IF NOT EXISTS budget_bullion_id BIGINT;
ALTER TABLE taurus.budget_months ADD COLUMN IF NOT EXISTS snapshot_planned DECIMAL(19,2);
ALTER TABLE taurus.budget_months ADD COLUMN IF NOT EXISTS snapshot_opening_balance DECIMAL(19,2);
ALTER TABLE taurus.budget_months ADD COLUMN IF NOT EXISTS snapshot_funding DECIMAL(19,2);
ALTER TABLE taurus.budget_months ADD COLUMN IF NOT EXISTS snapshot_spent DECIMAL(19,2);
ALTER TABLE taurus.budget_months ADD COLUMN IF NOT EXISTS snapshot_closing_balance DECIMAL(19,2);
ALTER TABLE taurus.budget_months ADD COLUMN IF NOT EXISTS remainder_transferred DECIMAL(19,2);
ALTER TABLE taurus.budget_months ADD COLUMN IF NOT EXISTS remainder_target_bullion_id BIGINT;
ALTER TABLE taurus.budget_months ADD COLUMN IF NOT EXISTS close_transaction_id BIGINT;

ALTER TABLE taurus.budget_months
    ADD CONSTRAINT fk_budget_months_budget_bullion FOREIGN KEY (budget_bullion_id) REFERENCES taurus.bullions(id) ON DELETE RESTRICT;
ALTER TABLE taurus.budget_months
    ADD CONSTRAINT fk_budget_months_remainder_target FOREIGN KEY (remainder_target_bullion_id) REFERENCES taurus.bullions(id) ON DELETE RESTRICT;
ALTER TABLE taurus.budget_months
    ADD CONSTRAINT fk_budget_months_close_transaction FOREIGN KEY (close_transaction_id) REFERENCES taurus.transactions(id) ON DELETE SET NULL;

COMMENT ON COLUMN taurus.budget_months.closed_at IS 'NULL — месяц открыт. Заполняется closeMonth, снимается reopenMonth';
COMMENT ON COLUMN taurus.budget_months.budget_bullion_id IS 'Каким слитком считали снимок — на случай смены бюджетного слитка в настройках';
COMMENT ON COLUMN taurus.budget_months.snapshot_planned IS 'План на момент закрытия. NULL, если план не задавали';

--rollback ALTER TABLE taurus.budget_months DROP CONSTRAINT IF EXISTS fk_budget_months_close_transaction;
--rollback ALTER TABLE taurus.budget_months DROP CONSTRAINT IF EXISTS fk_budget_months_remainder_target;
--rollback ALTER TABLE taurus.budget_months DROP CONSTRAINT IF EXISTS fk_budget_months_budget_bullion;
--rollback ALTER TABLE taurus.budget_months DROP COLUMN IF EXISTS close_transaction_id;
--rollback ALTER TABLE taurus.budget_months DROP COLUMN IF EXISTS remainder_target_bullion_id;
--rollback ALTER TABLE taurus.budget_months DROP COLUMN IF EXISTS remainder_transferred;
--rollback ALTER TABLE taurus.budget_months DROP COLUMN IF EXISTS snapshot_closing_balance;
--rollback ALTER TABLE taurus.budget_months DROP COLUMN IF EXISTS snapshot_spent;
--rollback ALTER TABLE taurus.budget_months DROP COLUMN IF EXISTS snapshot_funding;
--rollback ALTER TABLE taurus.budget_months DROP COLUMN IF EXISTS snapshot_opening_balance;
--rollback ALTER TABLE taurus.budget_months DROP COLUMN IF EXISTS snapshot_planned;
--rollback ALTER TABLE taurus.budget_months DROP COLUMN IF EXISTS budget_bullion_id;
--rollback ALTER TABLE taurus.budget_months DROP COLUMN IF EXISTS closed_at;
--rollback ALTER TABLE taurus.budget_months ALTER COLUMN planned_amount SET DEFAULT 0;
--rollback ALTER TABLE taurus.budget_months ALTER COLUMN planned_amount SET NOT NULL;
