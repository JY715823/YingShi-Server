alter table albums add column if not exists system_key varchar(80);
alter table albums add column if not exists include_in_photo_feed boolean not null default true;

create unique index if not exists uk_albums_library_system_key
    on albums (library_id, system_key)
    where system_key is not null;

alter table small_albums add column if not exists system_key varchar(80);

create unique index if not exists uk_small_albums_library_album_system_key
    on small_albums (library_id, album_id, system_key)
    where system_key is not null;

alter table media add column if not exists record_owner_user_id varchar(255);
alter table media add column if not exists uploaded_by_user_id varchar(255);

alter table upload_tasks add column if not exists uploaded_by_user_id varchar(255);

create table if not exists bowel_events (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    user_id varchar(255) not null,
    occurred_at_millis bigint not null,
    constraint bowel_events_pkey primary key (id)
);

create index if not exists idx_bowel_events_library_user_time
    on bowel_events (library_id, user_id, occurred_at_millis desc);

create table if not exists push_device_tokens (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    user_id varchar(255) not null,
    token varchar(512) not null,
    platform varchar(40) not null,
    enabled boolean not null default true,
    last_seen_at_millis bigint not null,
    constraint push_device_tokens_pkey primary key (id),
    constraint uk_push_device_tokens_token unique (token)
);

create index if not exists idx_push_device_tokens_library_enabled
    on push_device_tokens (library_id, enabled);
