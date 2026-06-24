alter table ledger_snapshots
    add column if not exists last_modified_by varchar(255);
