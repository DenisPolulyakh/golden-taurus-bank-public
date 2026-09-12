--liquibase formatted sql

--changeset dpolulyakh:credit_cards_payment_amount
--comment: Сумма, которую нужно внести к дате платежа — по кредитке платят не весь долг, а минималку; на лимит и остаток не влияет
ALTER TABLE taurus.credit_cards ADD COLUMN IF NOT EXISTS payment_amount DECIMAL(19,2);
ALTER TABLE taurus.credit_cards ADD CONSTRAINT chk_credit_cards_payment_amount CHECK (payment_amount IS NULL OR payment_amount >= 0);

--rollback ALTER TABLE taurus.credit_cards DROP CONSTRAINT IF EXISTS chk_credit_cards_payment_amount;
--rollback ALTER TABLE taurus.credit_cards DROP COLUMN IF EXISTS payment_amount;
