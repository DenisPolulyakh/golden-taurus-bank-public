--liquibase formatted sql

--changeset dpolulyakh:drop_card_number_enc
--comment: Полный номер карты больше не хранится: для операций он не нужен, остаются последние 4 цифры для поиска и маски
ALTER TABLE taurus.credit_cards DROP COLUMN IF EXISTS card_number_enc;

--rollback ALTER TABLE taurus.credit_cards ADD COLUMN card_number_enc VARCHAR(512) NOT NULL DEFAULT '';
