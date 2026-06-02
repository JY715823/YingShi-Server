create table if not exists ledger_snapshots (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    payload_json text not null,
    constraint ledger_snapshots_pkey primary key (id),
    constraint uk_ledger_snapshots_library_id unique (library_id)
);
