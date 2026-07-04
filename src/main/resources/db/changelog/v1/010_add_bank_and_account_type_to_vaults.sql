-- db/changelog/v1/010_add_bank_and_account_type_to_vaults.sql

-- Добавляем колонку bank_id
ALTER TABLE taurus.vaults ADD COLUMN IF NOT EXISTS bank_id BIGINT;

-- Добавляем внешний ключ на банки
ALTER TABLE taurus.vaults ADD CONSTRAINT fk_vaults_bank FOREIGN KEY (bank_id) REFERENCES taurus.bank_dictionary(id) ON DELETE SET NULL;

-- Добавляем колонку account_type
ALTER TABLE taurus.vaults ADD COLUMN IF NOT EXISTS account_type VARCHAR(30) DEFAULT 'SAVINGS' NOT NULL;

-- Добавляем колонку close_date
ALTER TABLE taurus.vaults ADD COLUMN IF NOT EXISTS close_date DATE;

-- Создаем индекс для поиска по банку
CREATE INDEX IF NOT EXISTS idx_vaults_bank_id ON taurus.vaults(bank_id);

-- Комментарии
COMMENT ON COLUMN taurus.vaults.bank_id IS 'ID банка из справочника банков';
COMMENT ON COLUMN taurus.vaults.account_type IS 'Тип счета: SAVINGS - Накопительный, TERM - Срочный';
COMMENT ON COLUMN taurus.vaults.close_date IS 'Дата закрытия для срочных вкладов';