--liquibase formatted sql

--changeset dpolulyakh:schema_taurus
--comment: Создание схемы taurus
CREATE SCHEMA IF NOT EXISTS taurus;

--changeset dpolulyakh:users_table
--comment: Создание таблицы пользователей
CREATE TABLE IF NOT EXISTS taurus.users (
    id                 BIGSERIAL PRIMARY KEY,
    email              VARCHAR(255) NOT NULL UNIQUE,
    password           VARCHAR(255) NOT NULL,
    full_name          VARCHAR(255) NOT NULL,
    email_verified     BOOLEAN      NOT NULL DEFAULT FALSE,
    verification_token VARCHAR(255),
    role               VARCHAR(50)  NOT NULL DEFAULT 'USER',
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    blocked_reason     VARCHAR(500),
    blocked_by         BIGINT,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

--changeset dpolulyakh:users_indexes
--comment: Индексы для пользователей
CREATE INDEX IF NOT EXISTS idx_users_email ON taurus.users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON taurus.users(role);
CREATE INDEX IF NOT EXISTS idx_users_is_active ON taurus.users(is_active);

--rollback DROP TABLE IF EXISTS taurus.users;
