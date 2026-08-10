--liquibase formatted sql

--changeset dpolulyakh:bullions_table
--comment: Слитки — наименование в конкретном хранилище; удалить слиток с историей нельзя (FK RESTRICT из transactions), вместо удаления archived
CREATE TABLE IF NOT EXISTS taurus.bullions (
    id              BIGSERIAL PRIMARY KEY,
    bullion_name_id BIGINT        NOT NULL,
    vault_id        BIGINT,
    amount          DECIMAL(19,2) NOT NULL DEFAULT 0,
    description     VARCHAR(500),
    user_id         BIGINT        NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived        BOOLEAN       NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_bullions_bullion_name FOREIGN KEY (bullion_name_id) REFERENCES taurus.bullion_names(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bullions_vault FOREIGN KEY (vault_id) REFERENCES taurus.vaults(id) ON DELETE SET NULL,
    CONSTRAINT fk_bullions_user FOREIGN KEY (user_id) REFERENCES taurus.users(id) ON DELETE CASCADE,
    CONSTRAINT uk_bullions_user_bullion_name_vault UNIQUE (user_id, bullion_name_id, vault_id)
);

--changeset dpolulyakh:bullions_indexes
--comment: Индексы для слитков, включая частичный под выборку неархивных
CREATE INDEX IF NOT EXISTS idx_bullions_user_id ON taurus.bullions(user_id);
CREATE INDEX IF NOT EXISTS idx_bullions_bullion_name_id ON taurus.bullions(bullion_name_id);
CREATE INDEX IF NOT EXISTS idx_bullions_vault_id ON taurus.bullions(vault_id);
CREATE INDEX IF NOT EXISTS idx_bullions_amount ON taurus.bullions(amount);
CREATE INDEX IF NOT EXISTS idx_bullions_user_active ON taurus.bullions(user_id) WHERE NOT archived;

--rollback DROP TABLE IF EXISTS taurus.bullions;
