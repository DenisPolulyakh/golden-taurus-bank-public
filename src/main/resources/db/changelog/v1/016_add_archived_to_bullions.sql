--liquibase formatted sql

--changeset dpolulyakh:016_0_add_archived_to_bullions
--comment: Слиток с историей нельзя удалить физически (FK RESTRICT из transactions) — вместо удаления архивируем
ALTER TABLE taurus.bullions ADD COLUMN IF NOT EXISTS archived BOOLEAN NOT NULL DEFAULT FALSE;

--changeset dpolulyakh:016_1_create_index_bullions_archived
--comment: Индекс под выборку активных слитков пользователя
CREATE INDEX IF NOT EXISTS idx_bullions_user_active ON taurus.bullions(user_id) WHERE NOT archived;

--rollback DROP INDEX IF EXISTS taurus.idx_bullions_user_active;
--rollback ALTER TABLE taurus.bullions DROP COLUMN IF EXISTS archived;
