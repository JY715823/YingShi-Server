alter table albums
    add column if not exists deleted_at timestamp(6) with time zone;

create index if not exists idx_albums_library_deleted_title
    on albums (library_id, deleted_at, title);

alter table trash_items
    drop constraint if exists trash_items_item_type_check;

alter table trash_items
    add constraint trash_items_item_type_check
        check (item_type in ('LARGE_ALBUM_DELETED', 'SMALL_ALBUM_DELETED', 'MEDIA_REMOVED', 'MEDIA_SYSTEM_DELETED'));
