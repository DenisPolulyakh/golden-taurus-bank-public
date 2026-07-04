--liquibase formatted sql
--changeset dpolulyakh:007_not_null_constraint_drop
--comment: удаление ограничения для  from_bullion_amount_before DECIMAL(19, 2) NOT NULL,
ALTER TABLE taurus.transaction_logs ALTER COLUMN from_bullion_amount_before DROP NOT NULL;