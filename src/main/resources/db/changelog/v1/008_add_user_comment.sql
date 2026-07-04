--liquibase formatted sql
--changeset dpolulyakh:008_add_user_comment
--comment: добавление поля user_comment
ALTER TABLE taurus.transaction_logs ADD COLUMN IF NOT EXISTS user_comment text;