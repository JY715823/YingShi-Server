create table auth_login_challenges (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    user_id varchar(255) not null,
    account varchar(120) not null,
    code_hash varchar(255) not null,
    expire_at timestamp(6) with time zone not null,
    resend_available_at timestamp(6) with time zone not null,
    last_sent_at timestamp(6) with time zone not null,
    send_count integer not null,
    failed_attempts integer not null,
    consumed_at timestamp(6) with time zone,
    invalidated_at timestamp(6) with time zone,
    constraint auth_login_challenges_pkey primary key (id)
);

create index idx_auth_login_challenges_account on auth_login_challenges (account);
create index idx_auth_login_challenges_user_id on auth_login_challenges (user_id);
create index idx_auth_login_challenges_expire_at on auth_login_challenges (expire_at);
