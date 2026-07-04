--liquibase formatted sql

--changeset dpolulyakh:002_0_create_categories_table
--comment: Создание таблицы категорий пользователя
create table if not exists taurus.categories (
    id bigserial primary key,
    name varchar(100) not null,
    user_id bigint not null,
    created_at timestamp default current_timestamp not null,
    updated_at timestamp default current_timestamp not null,
    constraint fk_categories_user foreign key (user_id) references taurus.users(id) on delete cascade,
    constraint uk_categories_user_name unique (user_id, name)
    );

--changeset dpolulyakh:002_1_create_indexes_categories
--comment: Индексы для категорий
create index if not exists idx_categories_user_id on taurus.categories(user_id);
create index if not exists idx_categories_name on taurus.categories(name);
