-- V30: Domain-aware unique constraints
-- The original unique indexes on (library_id, system_key) did not include domain,
-- causing duplicate key violations after domain isolation (V26).
-- Fix: recreate indexes with domain column included.

-- albums: (library_id, system_key) -> (library_id, system_key, domain)
DROP INDEX IF EXISTS uk_albums_library_system_key;
CREATE UNIQUE INDEX uk_albums_library_system_key
    ON albums (library_id, system_key, domain)
    WHERE system_key IS NOT NULL;

-- small_albums: (library_id, album_id, system_key) -> (library_id, album_id, system_key, domain)
DROP INDEX IF EXISTS uk_small_albums_library_album_system_key;
CREATE UNIQUE INDEX uk_small_albums_library_album_system_key
    ON small_albums (library_id, album_id, system_key, domain)
    WHERE system_key IS NOT NULL;
