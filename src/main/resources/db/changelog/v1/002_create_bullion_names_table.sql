--liquibase formatted sql

--changeset dpolulyakh:bullion_names_table
--comment: Наименования слитков — справочник пользователя (не категория трат: аналитику даёт сам слиток)
CREATE TABLE IF NOT EXISTS taurus.bullion_names (
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(100) NOT NULL,
    user_id    BIGINT       NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    color      TEXT,

    CONSTRAINT fk_bullion_names_user FOREIGN KEY (user_id) REFERENCES taurus.users(id) ON DELETE CASCADE,
    CONSTRAINT uk_bullion_names_user_title UNIQUE (user_id, title)
);

--changeset dpolulyakh:bullion_names_indexes
--comment: Индексы для наименований слитков
CREATE INDEX IF NOT EXISTS idx_bullion_names_user_id ON taurus.bullion_names(user_id);
CREATE INDEX IF NOT EXISTS idx_bullion_names_title ON taurus.bullion_names(title);

--rollback DROP TABLE IF EXISTS taurus.bullion_names;
