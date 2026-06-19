-- ============================================================
-- Cleanup script for orphaned data in upload_tasks table
-- Run this when Docker is ready:
--   docker exec -i yingshi-postgres psql -U yingshi -d yingshi < scripts/cleanup-orphaned-data.sql
-- ============================================================

-- 1. Show all dangling upload_tasks (SUCCESS but no matching media record)
SELECT '=== Dangling SUCCESS tasks (no matching media) ===' as info;
SELECT ut.id, ut.state, ut.stored_path, ut.created_at::date
FROM upload_tasks ut
WHERE ut.stored_path IS NOT NULL
  AND ut.stored_path NOT IN (SELECT storage_path FROM media WHERE deleted_at IS NULL)
ORDER BY ut.created_at;

-- 2. Show expired WAITING tasks
SELECT '=== Expired WAITING tasks ===' as info;
SELECT id, state, created_at::date, expire_at
FROM upload_tasks
WHERE state = 'WAITING' AND expire_at < NOW()
ORDER BY created_at;

-- 3. Show CANCELLED tasks
SELECT '=== CANCELLED tasks ===' as info;
SELECT id, state, stored_path, created_at::date
FROM upload_tasks
WHERE state = 'CANCELLED';

-- 4. Show dismissed tasks
SELECT '=== Dismissed tasks (hidden from UI) ===' as info;
SELECT COUNT(*) as dismissed_count
FROM upload_tasks
WHERE dismissed_at IS NOT NULL;

-- ============================================================
-- WARNING: Only run DELETEs after reviewing the output above
-- ============================================================

-- Delete dangling SUCCESS tasks (referencing non-existent media)
-- DELETE FROM upload_tasks
-- WHERE stored_path IS NOT NULL
--   AND stored_path NOT IN (SELECT storage_path FROM media WHERE deleted_at IS NULL);

-- Delete expired WAITING tasks (no stored_path, so no MinIO cleanup needed)
-- DELETE FROM upload_tasks
-- WHERE state = 'WAITING' AND expire_at < NOW();

-- Delete CANCELLED tasks (no stored_path)
-- DELETE FROM upload_tasks
-- WHERE state = 'CANCELLED';
