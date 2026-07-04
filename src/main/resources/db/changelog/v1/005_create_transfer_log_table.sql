--liquibase formatted sql

--changeset dpolulyakh:005_create_transfer_log_table
--comment: Создание таблицы истории транзакций
-- Создание таблицы transaction_logs
CREATE TABLE IF NOT EXISTS taurus.transaction_logs (
    id BIGSERIAL PRIMARY KEY,
    operation_type VARCHAR(50) NOT NULL,
    from_bullion_id BIGINT,
    to_bullion_id BIGINT,
    from_vault_id BIGINT,
    to_vault_id BIGINT,
    category_id BIGINT,
    amount DECIMAL(19, 2),
    from_bullion_amount_before DECIMAL(19, 2) NOT NULL,
    from_bullion_amount_after DECIMAL(19, 2),
    to_bullion_amount_before DECIMAL(19, 2),
    to_bullion_amount_after DECIMAL(19, 2),
    user_id BIGINT NOT NULL,
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    batch_id BIGINT,
    parent_transaction_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- Индексы для оптимизации запросов
CREATE INDEX idx_transaction_logs_user_id ON taurus.transaction_logs(user_id);
CREATE INDEX idx_transaction_logs_created_at ON taurus.transaction_logs(created_at);
CREATE INDEX idx_transaction_logs_operation_type ON taurus.transaction_logs(operation_type);
CREATE INDEX idx_transaction_logs_status ON taurus.transaction_logs(status);
CREATE INDEX idx_transaction_logs_batch_id ON taurus.transaction_logs(batch_id);
CREATE INDEX idx_transaction_logs_parent_transaction_id ON taurus.transaction_logs(parent_transaction_id);
CREATE INDEX idx_transaction_logs_from_bullion_id ON taurus.transaction_logs(from_bullion_id);
CREATE INDEX idx_transaction_logs_to_bullion_id ON taurus.transaction_logs(to_bullion_id);

-- Составные индексы для частых запросов
CREATE INDEX idx_transaction_logs_user_status ON taurus.transaction_logs(user_id, status);
CREATE INDEX idx_transaction_logs_user_created ON taurus.transaction_logs(user_id, created_at DESC);
CREATE INDEX idx_transaction_logs_batch_status ON taurus.transaction_logs(batch_id, status);

-- Комментарии к таблице и колонкам
COMMENT ON TABLE taurus.transaction_logs IS 'Логи всех транзакций для возможности отката и аудита';

COMMENT ON COLUMN taurus.transaction_logs.id IS 'Уникальный идентификатор лога';
COMMENT ON COLUMN taurus.transaction_logs.operation_type IS 'Тип операции: TRANSFER_AMOUNT, TRANSFER_BULLION, ROLLBACK_*, REDO_*';
COMMENT ON COLUMN taurus.transaction_logs.from_bullion_id IS 'ID слитка-отправителя';
COMMENT ON COLUMN taurus.transaction_logs.to_bullion_id IS 'ID слитка-получателя';
COMMENT ON COLUMN taurus.transaction_logs.from_vault_id IS 'ID хранилища-отправителя';
COMMENT ON COLUMN taurus.transaction_logs.to_vault_id IS 'ID хранилища-получателя';
COMMENT ON COLUMN taurus.transaction_logs.category_id IS 'ID категории слитка';
COMMENT ON COLUMN taurus.transaction_logs.amount IS 'Сумма операции';
COMMENT ON COLUMN taurus.transaction_logs.from_bullion_amount_before IS 'Сумма в слитке-отправителе ДО операции';
COMMENT ON COLUMN taurus.transaction_logs.from_bullion_amount_after IS 'Сумма в слитке-отправителе ПОСЛЕ операции';
COMMENT ON COLUMN taurus.transaction_logs.to_bullion_amount_before IS 'Сумма в слитке-получателе ДО операции';
COMMENT ON COLUMN taurus.transaction_logs.to_bullion_amount_after IS 'Сумма в слитке-получателе ПОСЛЕ операции';
COMMENT ON COLUMN taurus.transaction_logs.user_id IS 'ID пользователя, выполнившего операцию';
COMMENT ON COLUMN taurus.transaction_logs.description IS 'Описание операции';
COMMENT ON COLUMN taurus.transaction_logs.status IS 'Статус: SUCCESS, FAILED, ROLLED_BACK, REDONE';
COMMENT ON COLUMN taurus.transaction_logs.error_message IS 'Сообщение об ошибке в случае FAILED';
COMMENT ON COLUMN taurus.transaction_logs.batch_id IS 'ID группы операций для массовых операций';
COMMENT ON COLUMN taurus.transaction_logs.parent_transaction_id IS 'ID родительской транзакции для откатов/повторов';
COMMENT ON COLUMN taurus.transaction_logs.created_at IS 'Дата и время создания лога';