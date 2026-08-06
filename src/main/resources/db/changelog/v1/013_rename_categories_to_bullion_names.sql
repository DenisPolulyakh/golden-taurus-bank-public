--liquibase formatted sql

--changeset dpolulyakh:013_0_rename_categories_to_bullion_names
--comment: Переименование categories в bullion_names — это наименование слитка, а не категория трат
ALTER TABLE taurus.categories RENAME TO bullion_names;
ALTER TABLE taurus.bullion_names RENAME COLUMN name TO title;
ALTER TABLE taurus.bullion_names RENAME CONSTRAINT fk_categories_user TO fk_bullion_names_user;
ALTER TABLE taurus.bullion_names RENAME CONSTRAINT uk_categories_user_name TO uk_bullion_names_user_title;
ALTER INDEX taurus.idx_categories_user_id RENAME TO idx_bullion_names_user_id;
ALTER INDEX taurus.idx_categories_name RENAME TO idx_bullion_names_title;

--changeset dpolulyakh:013_1_rename_bullions_category_id
--comment: Переименование bullions.category_id в bullion_name_id вслед за bullion_names
ALTER TABLE taurus.bullions RENAME COLUMN category_id TO bullion_name_id;
ALTER TABLE taurus.bullions RENAME CONSTRAINT fk_bullions_category TO fk_bullions_bullion_name;
ALTER TABLE taurus.bullions RENAME CONSTRAINT uk_bullions_user_category_vault TO uk_bullions_user_bullion_name_vault;
ALTER INDEX taurus.idx_bullions_category_id RENAME TO idx_bullions_bullion_name_id;
