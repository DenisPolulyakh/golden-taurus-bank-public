--liquibase formatted sql

--changeset dpolulyakh:credit_card_history_table
--comment: История операций по карте. Живёт отдельно от transactions: там ноги — слитки, здесь единственный участник — карта
CREATE TABLE IF NOT EXISTS taurus.credit_card_history (
    id                     BIGSERIAL PRIMARY KEY,
    credit_card_id         BIGINT        NOT NULL,
    user_id                BIGINT        NOT NULL,
    operation              VARCHAR(20)   NOT NULL,
    amount                 DECIMAL(19,2) NOT NULL CHECK (amount > 0),
    debt_after             DECIMAL(19,2) NOT NULL,
    date_operation         TIMESTAMP     NOT NULL,
    created_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    comment                VARCHAR(500),
    reversal_of_id         BIGINT,
    bullion_transaction_id BIGINT,

    CONSTRAINT fk_cch_credit_card FOREIGN KEY (credit_card_id) REFERENCES taurus.credit_cards(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cch_user FOREIGN KEY (user_id) REFERENCES taurus.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_cch_reversal_of FOREIGN KEY (reversal_of_id) REFERENCES taurus.credit_card_history(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cch_bullion_transaction FOREIGN KEY (bullion_transaction_id) REFERENCES taurus.transactions(id) ON DELETE RESTRICT
);

COMMENT ON COLUMN taurus.credit_card_history.debt_after IS 'Долг после операции — история показывает его без пересчёта цепочки';
COMMENT ON COLUMN taurus.credit_card_history.bullion_transaction_id IS 'Погашение со слитка: вторая нога операции в taurus.transactions';

--changeset dpolulyakh:credit_card_history_indexes
--comment: Выборка истории карты, поиск отката и связки со слитком
CREATE INDEX IF NOT EXISTS idx_cch_credit_card_id ON taurus.credit_card_history(credit_card_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_cch_bullion_transaction_id ON taurus.credit_card_history(bullion_transaction_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_cch_reversal_of_id ON taurus.credit_card_history(reversal_of_id) WHERE reversal_of_id IS NOT NULL;

--rollback DROP TABLE IF EXISTS taurus.credit_card_history;
