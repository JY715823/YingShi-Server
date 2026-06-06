do $$
begin
    if exists (
        select 1
        from information_schema.columns
        where table_schema = 'public'
          and table_name = 'trash_items'
          and column_name = 'snapshot_json'
          and data_type = 'oid'
    ) then
        alter table trash_items add column if not exists snapshot_json_text text;

        update trash_items t
        set snapshot_json_text = case
            when exists (
                select 1
                from pg_largeobject_metadata lom
                where lom.oid = t.snapshot_json
            ) then convert_from(lo_get(t.snapshot_json), 'UTF8')
            else t.snapshot_json::text
        end
        where t.snapshot_json_text is null;

        alter table trash_items alter column snapshot_json_text set not null;
        alter table trash_items drop column snapshot_json;
        alter table trash_items rename column snapshot_json_text to snapshot_json;
    end if;
end $$;

update trash_items
set item_type = 'SMALL_ALBUM_DELETED'
where item_type = 'POST_DELETED';

alter table if exists trash_items
    drop constraint if exists trash_items_item_type_check;

alter table if exists trash_items
    add constraint trash_items_item_type_check
    check (item_type in ('SMALL_ALBUM_DELETED', 'MEDIA_REMOVED', 'MEDIA_SYSTEM_DELETED'));

create index if not exists idx_albums_library_title
    on albums (library_id, title, id);

create index if not exists idx_shared_library_members_user_library
    on shared_library_members (user_id, library_id);

create index if not exists idx_media_library_deleted_display
    on media (library_id, deleted_at, display_time_millis desc, id);

create index if not exists idx_media_library_source_fingerprint_active
    on media (library_id, source_fingerprint)
    where deleted_at is null and source_fingerprint is not null;

create index if not exists idx_media_library_duplicate_active
    on media (library_id, media_type, mime_type, size_bytes, display_time_millis, width, height, duration_millis)
    where deleted_at is null;

create index if not exists idx_small_albums_library_deleted_display
    on small_albums (library_id, deleted_at, display_time_millis desc, updated_at desc, id);

create index if not exists idx_small_albums_library_album_deleted_display
    on small_albums (library_id, album_id, deleted_at, display_time_millis desc, updated_at desc, id);

create index if not exists idx_small_album_media_library_media
    on small_album_media (library_id, media_id);

create index if not exists idx_comments_library_target_album_created
    on comments (library_id, target_type, small_album_id, created_at desc, id desc);

create index if not exists idx_comments_library_target_media_created
    on comments (library_id, target_type, media_id, created_at desc, id desc);

create index if not exists idx_comments_library_created
    on comments (library_id, created_at desc, id desc);

create index if not exists idx_trash_items_library_state_deleted
    on trash_items (library_id, state, deleted_at desc, id desc);

create index if not exists idx_trash_items_library_state_type_deleted
    on trash_items (library_id, state, item_type, deleted_at desc, id desc);

create index if not exists idx_trash_items_state_undo_deadline
    on trash_items (state, undo_deadline_at);

create index if not exists idx_trash_items_library_updated
    on trash_items (library_id, updated_at desc, id desc);

create index if not exists idx_upload_tasks_library_updated
    on upload_tasks (library_id, updated_at desc, id desc);

create index if not exists idx_upload_tasks_library_state_expire
    on upload_tasks (library_id, state, expire_at);

create index if not exists idx_upload_tasks_library_media
    on upload_tasks (library_id, media_id)
    where media_id is not null;

create index if not exists idx_notification_reads_user_notification_read
    on notification_reads (user_id, notification_id, read_at desc);

create index if not exists idx_auth_sessions_user_library_active
    on auth_sessions (user_id, library_id, refresh_expire_at desc)
    where revoked_at is null;

create index if not exists idx_auth_sessions_refresh_token
    on auth_sessions (refresh_token_id);

create index if not exists idx_ledger_snapshots_library_updated
    on ledger_snapshots (library_id, updated_at desc);

create index if not exists idx_chat_snapshots_library_updated
    on chat_snapshots (library_id, updated_at desc);

create index if not exists idx_bowel_events_library_time
    on bowel_events (library_id, occurred_at_millis);

create index if not exists idx_push_device_tokens_library_enabled_seen
    on push_device_tokens (library_id, enabled, last_seen_at_millis desc);
