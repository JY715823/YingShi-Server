create table if not exists push_preferences (
    id varchar(255) not null,
    library_id varchar(255) not null,
    user_id varchar(255) not null,
    module varchar(40) not null,
    category varchar(80) not null,
    enabled boolean not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint push_preferences_pkey primary key (id),
    constraint uk_push_preferences_user_module_category unique (user_id, module, category)
);

create index if not exists idx_push_preferences_user
    on push_preferences (user_id);
