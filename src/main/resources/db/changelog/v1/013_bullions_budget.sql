--liquibase formatted sql
--changeset dpolulyakh:bullions_budget
--comment: Признак бюджетного слитка: по нему считается отчёт «Бюджет на месяц»
ALTER TABLE taurus.bullions
    ADD COLUMN IF NOT EXISTS budget BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN taurus.bullions.budget IS 'Слиток текущих расходов, по которому считается месячный бюджет';

  -- Не больше одного активного бюджетного слитка на пользователя.
  -- Архивные не считаются: старый бюджетный слиток остаётся ради истории прошлых лет.
CREATE UNIQUE INDEX IF NOT EXISTS uq_bullions_budget_per_user
    ON taurus.bullions (user_id) WHERE budget AND NOT archived;

--rollback DROP INDEX IF EXISTS taurus.uq_bullions_budget_per_user;
--rollback ALTER TABLE taurus.bullions DROP COLUMN IF EXISTS budget;

--changeset dpolulyakh:bullions_budget_source
--comment: Слиток, с которого бюджет финансируется кнопкой «Профинансировать»
ALTER TABLE taurus.bullions
    ADD COLUMN IF NOT EXISTS budget_source BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN taurus.bullions.budget_source IS 'Слиток-источник финансирования месячного бюджета';

CREATE UNIQUE INDEX IF NOT EXISTS uq_bullions_budget_source_per_user
    ON taurus.bullions (user_id) WHERE budget_source AND NOT archived;

--rollback DROP INDEX IF EXISTS taurus.uq_bullions_budget_source_per_user;
--rollback ALTER TABLE taurus.bullions DROP COLUMN IF EXISTS budget_source;