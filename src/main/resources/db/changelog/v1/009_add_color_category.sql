--liquibase formatted sql
--changeset dpolulyakh:009_add_color_category
--comment: добавление поля color
ALTER TABLE taurus.categories ADD COLUMN IF NOT EXISTS color text;