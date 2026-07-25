--liquibase formatted sql
--changeset dpolulyakh:012_alter_table_transaction_log
--comment: изменения типа date_operation

-- 1. Добавляем новую колонку с типом TIMESTAMP
ALTER TABLE taurus.transaction_logs
    ADD COLUMN date_operation_new TIMESTAMP;

-- 2. Копируем данные (с конвертацией)
UPDATE taurus.transaction_logs
SET date_operation_new = date_operation::TIMESTAMP;

-- 3. Удаляем старую колонку
ALTER TABLE taurus.transaction_logs
DROP COLUMN date_operation;

-- 4. Переименовываем новую колонку
ALTER TABLE taurus.transaction_logs
    RENAME COLUMN date_operation_new TO date_operation


