--liquibase formatted sql

--changeset dpolulyakh:bank_dictionary_table
--comment: Справочник банков — заводится до хранилищ, vaults.bank_id ссылается на него
CREATE TABLE IF NOT EXISTS taurus.bank_dictionary (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    user_id    BIGINT       NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_bank_user FOREIGN KEY (user_id) REFERENCES taurus.users(id) ON DELETE CASCADE
);

--changeset dpolulyakh:bank_dictionary_indexes
--comment: Индексы для справочника банков
CREATE INDEX IF NOT EXISTS idx_bank_user_id ON taurus.bank_dictionary(user_id);
CREATE INDEX IF NOT EXISTS idx_bank_name ON taurus.bank_dictionary(name);
CREATE INDEX IF NOT EXISTS idx_bank_user_name ON taurus.bank_dictionary(user_id, name);

--rollback DROP TABLE IF EXISTS taurus.bank_dictionary;
