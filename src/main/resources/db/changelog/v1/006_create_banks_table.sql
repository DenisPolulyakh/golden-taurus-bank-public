--liquibase formatted sql

--changeset dpolulyakh:006_create_banks_table
--comment: Создание таблицы банков
CREATE TABLE IF NOT EXISTS taurus.bank_dictionary (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_bank_user FOREIGN KEY (user_id) REFERENCES taurus.users(id) ON DELETE CASCADE
    );

-- Индексы для быстрого поиска
CREATE INDEX IF NOT EXISTS idx_bank_user_id ON taurus.bank_dictionary(user_id);
CREATE INDEX IF NOT EXISTS idx_bank_name ON taurus.bank_dictionary(name);
CREATE INDEX IF NOT EXISTS idx_bank_user_name ON taurus.bank_dictionary(user_id, name);