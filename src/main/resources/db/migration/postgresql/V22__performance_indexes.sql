-- =============================================================================
-- V22: Performance indexes for sync cursors, listing queries, and feed lookups
-- Fills gaps left by V10/V15 for high-frequency repository methods
-- =============================================================================

-- ── small_albums (= PostEntity table) ──────────────────────────────────────

-- Sync cursor: findTopByLibraryIdAndDeletedAtIsNullAndSystemKeyIsNullOrderByUpdatedAtDesc
-- Sync cursor: findTopByLibraryIdAndSystemKeyIsNullOrderByUpdatedAtDesc
create index if not exists idx_small_albums_library_system_updated
    on small_albums (library_id, system_key, updated_at desc);

-- Listing: findByLibraryIdAndDeletedAtIsNullOrderByTitleAsc
create index if not exists idx_small_albums_library_deleted_title
    on small_albums (library_id, deleted_at, title, id);

-- Sync cursor: findTopByLibraryIdOrderByUpdatedAtDesc (all rows including deleted)
create index if not exists idx_small_albums_library_updated
    on small_albums (library_id, updated_at desc);

-- ── albums (AlbumEntity table) ─────────────────────────────────────────────

-- Sync cursor: findTopByLibraryIdAndDeletedAtIsNullAndSystemKeyIsNullOrderByUpdatedAtDesc
-- Sync cursor: findTopByLibraryIdAndSystemKeyIsNullOrderByUpdatedAtDesc
create index if not exists idx_albums_library_system_updated
    on albums (library_id, system_key, updated_at desc);

-- Sync cursor: findTopByLibraryIdOrderByUpdatedAtDesc (all rows including deleted)
create index if not exists idx_albums_library_updated
    on albums (library_id, updated_at desc);

-- ── post_media (= small_album_media, PostMediaEntity table) ────────────────

-- Feed lookup: findByLibraryIdAndPostIdOrderBySortOrderAsc
-- (unique constraint uk_small_album_media_small_album_sort covers this partially,
--  but a non-unique index is better for read-only queries without sort_order filtering)
create index if not exists idx_post_media_library_post_sort
    on small_album_media (library_id, small_album_id, sort_order);

-- Reverse lookup: findByLibraryIdAndMediaIdIn (used by MediaService feed + import status)
create index if not exists idx_post_media_library_media
    on small_album_media (library_id, media_id);

-- Sync cursor: findFirstByLibraryIdOrderByUpdatedAtDesc
create index if not exists idx_post_media_library_updated
    on small_album_media (library_id, updated_at desc);

-- ── comments ───────────────────────────────────────────────────────────────

-- General listing: findByLibraryIdOrderByCreatedAtDesc
-- V15 partial indexes all include target_type which is wasteful for non-targeted queries
create index if not exists idx_comments_library_created_simple
    on comments (library_id, created_at desc);

-- ── media ──────────────────────────────────────────────────────────────────

-- Batch lookup: findByLibraryIdAndIdIn / findByLibraryIdAndIdInAndDeletedAtIsNull
-- Existing idx_media_library_deleted_display leads with (library_id, deleted_at) which
-- doesn't efficiently support IN(id) lookups
create index if not exists idx_media_library_id_active
    on media (library_id, id)
    where deleted_at is null;

-- Batch fingerprint lookup: findByLibraryIdAndSourceFingerprintInAndDeletedAtIsNull
-- Existing partial index idx_media_library_source_fingerprint_active is on (library_id, source_fingerprint)
-- which already covers this — no additional index needed
