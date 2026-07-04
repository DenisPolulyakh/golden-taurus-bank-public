--liquibase formatted sql

--changeset dpolulyakh:001_create_schema
--comment: Создание схемы taurus
CREATE SCHEMA IF NOT EXISTS taurus;

--changeset dpolulyakh:002_create_users_table
--comment: Создание таблицы пользователей
CREATE TABLE IF NOT EXISTS taurus.users (
                                            id BIGSERIAL PRIMARY KEY,
                                            email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    email_verified BOOLEAN DEFAULT FALSE NOT NULL,
    verification_token VARCHAR(255),
    role VARCHAR(50) DEFAULT 'USER' NOT NULL,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    blocked_reason VARCHAR(500),
    blocked_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
    );

--changeset dpolulyakh:003_create_indexes
--comment: Создание индексов
CREATE INDEX IF NOT EXISTS idx_users_email ON taurus.users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON taurus.users(role);
CREATE INDEX IF NOT EXISTS idx_users_is_active ON taurus.users(is_active);



