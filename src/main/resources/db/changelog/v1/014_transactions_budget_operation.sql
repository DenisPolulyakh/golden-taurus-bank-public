--liquibase formatted sql

--changeset dpolulyakh:transactions_budget_operation
--comment: Корзина операции по бюджетному слитку: трата дня (true) или движение самого бюджета (false)
ALTER TABLE taurus.transactions
    ADD COLUMN IF NOT EXISTS budget_operation BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN taurus.transactions.budget_operation IS
      'true — трата дня, false — движение самого бюджета (финансирование, докидывание, перенос остатка, доход прямо в кошелёк).
  Значимо только для операций по бюджетному слитку';

  -- Стартовый остаток слитка — всегда движение бюджета, а не трата.
  -- Иначе первый же день истории покажет расход на всю сумму накоплений.
UPDATE taurus.transactions SET budget_operation = FALSE WHERE opening_balance;

--rollback ALTER TABLE taurus.transactions DROP COLUMN IF EXISTS budget_operation;

