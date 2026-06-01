create table auth_sessions (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    user_id varchar(255) not null,
    refresh_token_id varchar(255) not null,
    refresh_expire_at timestamp(6) with time zone not null,
    last_authenticated_at timestamp(6) with time zone not null,
    revoked_at timestamp(6) with time zone,
    constraint auth_sessions_pkey primary key (id)
);

create index idx_auth_sessions_user_id on auth_sessions (user_id);
create index idx_auth_sessions_library_id on auth_sessions (library_id);
create index idx_auth_sessions_revoked_at on auth_sessions (revoked_at);

alter table comments add column if not exists last_edited_by_user_id varchar(255);
alter table comments add column if not exists deleted_by_user_id varchar(255);
