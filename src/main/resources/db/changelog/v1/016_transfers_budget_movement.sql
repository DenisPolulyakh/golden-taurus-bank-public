--liquibase formatted sql

--changeset dpolulyakh:transfers_budget_movement
--comment: Переводы — движение бюджета, а не трата дня. Миграция 014 проставила тратой всё подряд, из-за чего старое финансирование месяца читалось как возмещение и уводило день в минус
UPDATE taurus.transactions
SET budget_operation = FALSE
WHERE source_bullion_id IS NOT NULL
  AND target_bullion_id IS NOT NULL;

-- Обратно ставим тратой всё: точную разметку не восстановить, а умолчание
-- миграции 014 было именно таким
--rollback UPDATE taurus.transactions SET budget_operation = TRUE WHERE source_bullion_id IS NOT NULL AND target_bullion_id IS NOT NULL AND NOT opening_balance;
