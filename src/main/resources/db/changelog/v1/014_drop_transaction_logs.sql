--liquibase formatted sql

--changeset dpolulyakh:014_drop_transaction_logs
--comment: Старая модель транзакций заменяется новой таблицей transactions (см. 015)
DROP TABLE IF EXISTS taurus.transaction_logs;
