--liquibase formatted sql

--changeset dpolulyakh:003_0_create_vaults_table
--comment: Создание таблицы хранилищ с процентной ставкой
CREATE TABLE IF NOT EXISTS taurus.vaults (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    interest_rate DECIMAL(5,2) DEFAULT 0 NOT NULL,
    description VARCHAR(500),
    user_id BIGINT NOT NULL,
    vault_type VARCHAR(30) DEFAULT 'REGULAR' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_vaults_user FOREIGN KEY (user_id) REFERENCES taurus.users(id) ON DELETE CASCADE
    );

--changeset dpolulyakh:003_1_create_indexes_vaults
--comment: Индексы для хранилищ
CREATE INDEX IF NOT EXISTS idx_vaults_user_id ON taurus.vaults(user_id);
CREATE INDEX IF NOT EXISTS idx_vaults_name ON taurus.vaults(name);

--rollback DROP INDEX IF EXISTS taurus.idx_vaults_user_id;
--rollback DROP INDEX IF EXISTS taurus.idx_vaults_name;
--rollback DROP TABLE IF EXISTS taurus.vaults;