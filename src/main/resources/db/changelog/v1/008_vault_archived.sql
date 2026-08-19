--liquibase formatted sql

--changeset dpolulyakh:vaults_archived
--comment: Хранилище не удаляется физически, а архивируется — иначе история операций теряет хранилище и банк
ALTER TABLE taurus.vaults
    ADD COLUMN IF NOT EXISTS archived BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_vaults_user_active ON taurus.vaults (user_id) WHERE NOT archived;

--changeset dpolulyakh:vaults_restrict_fk
--comment: Связи слитка с хранилищем и хранилища с банком больше не обнуляются молча
ALTER TABLE taurus.bullions DROP CONSTRAINT IF EXISTS fk_bullions_vault;
ALTER TABLE taurus.bullions ADD CONSTRAINT fk_bullions_vault
    FOREIGN KEY (vault_id) REFERENCES taurus.vaults (id) ON DELETE RESTRICT;

ALTER TABLE taurus.vaults DROP CONSTRAINT IF EXISTS fk_vaults_bank;
ALTER TABLE taurus.vaults ADD CONSTRAINT fk_vaults_bank
    FOREIGN KEY (bank_id) REFERENCES taurus.bank_dictionary (id) ON DELETE RESTRICT;
