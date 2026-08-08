--liquibase formatted sql

--changeset dpolulyakh:transactions_table
--comment: Транзакция — одна запись с двумя необязательными ногами; тип операции выводится из заполненности source/target, не хранится
CREATE TABLE IF NOT EXISTS taurus.transactions (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT        NOT NULL,
    date_operation    TIMESTAMP     NOT NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    amount            DECIMAL(19,2) NOT NULL CHECK (amount > 0),
    source_bullion_id BIGINT,
    target_bullion_id BIGINT,
    opening_balance   BOOLEAN       NOT NULL DEFAULT FALSE,
    imported          BOOLEAN       NOT NULL DEFAULT FALSE,
    comment           VARCHAR(500),
    reversal_of_id    BIGINT,
    batch_id          BIGINT,

    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES taurus.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_transactions_source_bullion FOREIGN KEY (source_bullion_id) REFERENCES taurus.bullions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_target_bullion FOREIGN KEY (target_bullion_id) REFERENCES taurus.bullions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_reversal_of FOREIGN KEY (reversal_of_id) REFERENCES taurus.transactions(id) ON DELETE RESTRICT,
    CONSTRAINT chk_transactions_has_leg CHECK (source_bullion_id IS NOT NULL OR target_bullion_id IS NOT NULL),
    CONSTRAINT chk_transactions_not_self CHECK (source_bullion_id IS DISTINCT FROM target_bullion_id)
);

--changeset dpolulyakh:transactions_indexes
--comment: Индексы для истории транзакций, графика накоплений и отката
CREATE INDEX IF NOT EXISTS idx_transactions_user_date ON taurus.transactions(user_id, date_operation);
CREATE INDEX IF NOT EXISTS idx_transactions_source_bullion_id ON taurus.transactions(source_bullion_id);
CREATE INDEX IF NOT EXISTS idx_transactions_target_bullion_id ON taurus.transactions(target_bullion_id);
CREATE INDEX IF NOT EXISTS idx_transactions_reversal_of_id ON taurus.transactions(reversal_of_id);
CREATE INDEX IF NOT EXISTS idx_transactions_batch_id ON taurus.transactions(batch_id);

--rollback DROP TABLE IF EXISTS taurus.transactions;
