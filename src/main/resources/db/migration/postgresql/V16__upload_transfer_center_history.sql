alter table upload_tasks add column if not exists operation_id varchar(255);
alter table upload_tasks add column if not exists operation_type varchar(40);
alter table upload_tasks add column if not exists operation_title varchar(255);
alter table upload_tasks add column if not exists operation_media_count integer;
alter table upload_tasks add column if not exists source_item_id varchar(255);
alter table upload_tasks add column if not exists error_message varchar(512);
alter table upload_tasks add column if not exists dismissed_at timestamp(6) with time zone;

alter table upload_tasks drop constraint if exists upload_tasks_state_check;
alter table upload_tasks
    add constraint upload_tasks_state_check
    check (state in ('WAITING', 'SUCCESS', 'FAILED', 'CANCELLED'));

create index if not exists idx_upload_tasks_transfer_center_history
    on upload_tasks (library_id, uploaded_by_user_id, dismissed_at, updated_at desc, id desc);

create index if not exists idx_upload_tasks_transfer_operation
    on upload_tasks (library_id, operation_id, updated_at desc)
    where operation_id is not null;
