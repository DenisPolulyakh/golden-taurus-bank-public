--liquibase formatted sql

--changeset dpolulyakh:budget_months_table
--comment: План месяца по бюджету. Строка появляется, только когда пользователь задал сумму; без неё отчёт считается, просто без плана и перерасхода
CREATE TABLE IF NOT EXISTS taurus.budget_months (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT        NOT NULL,
    year           INT           NOT NULL,
    month          INT           NOT NULL,
    planned_amount DECIMAL(19,2) NOT NULL DEFAULT 0 CHECK (planned_amount >= 0),
    comment        VARCHAR(500),
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_budget_months_user FOREIGN KEY (user_id) REFERENCES taurus.users(id) ON DELETE CASCADE,
    CONSTRAINT chk_budget_months_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT chk_budget_months_year CHECK (year BETWEEN 2000 AND 2100)
);

COMMENT ON TABLE taurus.budget_months IS 'План расходов на месяц — «Бюджет» из блока A4 листа месяца в Excel';
COMMENT ON COLUMN taurus.budget_months.planned_amount IS 'Ориентир, а не лимит: потратить и профинансировать сверх него можно';

--rollback DROP TABLE IF EXISTS taurus.budget_months;

--changeset dpolulyakh:budget_months_unique
--comment: Один план на месяц у пользователя
CREATE UNIQUE INDEX IF NOT EXISTS uk_budget_months_user_period
    ON taurus.budget_months(user_id, year, month);

--rollback DROP INDEX IF EXISTS taurus.uk_budget_months_user_period;
