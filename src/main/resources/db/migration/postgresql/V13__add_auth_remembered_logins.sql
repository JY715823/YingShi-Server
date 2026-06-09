create table auth_remembered_logins (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    user_id varchar(255) not null,
    account varchar(120) not null,
    device_id varchar(160) not null,
    token_hash varchar(255) not null,
    expire_at timestamp(6) with time zone not null,
    last_authenticated_at timestamp(6) with time zone not null,
    last_used_at timestamp(6) with time zone not null,
    revoked_at timestamp(6) with time zone,
    constraint auth_remembered_logins_pkey primary key (id),
    constraint uk_auth_remembered_logins_user_device unique (user_id, device_id)
);

create index idx_auth_remembered_logins_user_id on auth_remembered_logins (user_id);
create index idx_auth_remembered_logins_account on auth_remembered_logins (account);
create index idx_auth_remembered_logins_expire_at on auth_remembered_logins (expire_at);
