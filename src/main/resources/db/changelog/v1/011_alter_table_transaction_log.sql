--liquibase formatted sql
--changeset dpolulyakh:011_alter_table_transaction_log
--comment: добавление поля date_operation
ALTER TABLE taurus.transaction_logs ADD COLUMN date_operation DATE;

UPDATE taurus.transaction_logs SET date_operation = created_at::DATE;

ALTER TABLE taurus.transaction_logs ALTER COLUMN date_operation SET NOT NULL;
