create table if not exists push_delivery_audits (
    id varchar(255) not null,
    library_id varchar(255) not null,
    actor_user_id varchar(255) not null,
    module varchar(40) not null,
    category varchar(80) not null,
    event_type varchar(40) not null,
    status varchar(40) not null,
    reason varchar(120) not null,
    target_route varchar(255) not null,
    enabled_device_count integer not null,
    partner_device_count integer not null,
    target_device_count integer not null,
    attempted_count integer not null,
    successful_count integer not null,
    invalid_token_count integer not null,
    used_self_fallback boolean not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint push_delivery_audits_pkey primary key (id)
);

create index if not exists idx_push_delivery_audits_library_created
    on push_delivery_audits (library_id, created_at desc);
