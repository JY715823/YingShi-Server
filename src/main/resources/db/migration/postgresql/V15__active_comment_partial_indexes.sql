create index if not exists idx_comments_active_library_target_album_created
    on comments (library_id, target_type, small_album_id, created_at desc, id desc)
    where deleted_at is null;

create index if not exists idx_comments_active_library_target_media_created
    on comments (library_id, target_type, media_id, created_at desc, id desc)
    where deleted_at is null;
