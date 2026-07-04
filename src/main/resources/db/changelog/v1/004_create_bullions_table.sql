--liquibase formatted sql

--changeset dpolulyakh:004_0_create_bullions_table
--comment: Создание таблицы слитков
CREATE TABLE IF NOT EXISTS taurus.bullions (
    id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL,
    vault_id BIGINT,
    amount DECIMAL(19,2) DEFAULT 0 NOT NULL,
    description VARCHAR(500),
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_bullions_category FOREIGN KEY (category_id) REFERENCES taurus.categories(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bullions_vault FOREIGN KEY (vault_id) REFERENCES taurus.vaults(id) ON DELETE SET NULL,
    CONSTRAINT fk_bullions_user FOREIGN KEY (user_id) REFERENCES taurus.users(id) ON DELETE CASCADE,
    CONSTRAINT uk_bullions_user_category_vault UNIQUE (user_id, category_id, vault_id)
    );

--changeset dpolulyakh:004_1_create_bullions_table
--comment: Индексы для слитков
CREATE INDEX IF NOT EXISTS idx_bullions_user_id ON taurus.bullions(user_id);
CREATE INDEX IF NOT EXISTS idx_bullions_category_id ON taurus.bullions(category_id);
CREATE INDEX IF NOT EXISTS idx_bullions_vault_id ON taurus.bullions(vault_id);
CREATE INDEX IF NOT EXISTS idx_bullions_amount ON taurus.bullions(amount);

--rollback DROP INDEX IF EXISTS taurus.idx_bullions_user_id;
--rollback DROP INDEX IF EXISTS taurus.idx_bullions_category_id;
--rollback DROP INDEX IF EXISTS taurus.idx_bullions_vault_id;
--rollback DROP INDEX IF EXISTS taurus.idx_bullions_amount;
--rollback DROP TABLE IF EXISTS taurus.bullions;