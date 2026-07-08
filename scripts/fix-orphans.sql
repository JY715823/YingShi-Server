-- ============================================================
-- V24 Foreign Key Orphan Fix Script
-- ============================================================
-- WARNING: Review each section before executing!
-- Soft fixes (SET NULL) are safe to run directly.
-- Hard fixes (DELETE) are COMMENTED OUT - uncomment only after review.
--
-- Usage:
--   1. Run audit-orphans.sql first to identify orphans
--   2. Review and uncomment relevant sections below
--   3. psql -h localhost -U yingshi -d yingshi -f scripts/fix-orphans.sql
--   4. Run audit-orphans.sql again to verify all counts are 0
-- ============================================================

BEGIN;

-- =====================================================================
-- SOFT FIX: SET NULL for optional references (safe to run)
-- These match V24's ON DELETE SET NULL constraints.
-- =====================================================================

-- comments.small_album_id (optional FK)
UPDATE comments SET small_album_id = NULL
    WHERE small_album_id IS NOT NULL
    AND small_album_id NOT IN (SELECT id FROM small_albums);

-- comments.media_id (optional FK)
UPDATE comments SET media_id = NULL
    WHERE media_id IS NOT NULL
    AND media_id NOT IN (SELECT id FROM media);

-- upload_tasks.media_id (optional FK)
UPDATE upload_tasks SET media_id = NULL
    WHERE media_id IS NOT NULL
    AND media_id NOT IN (SELECT id FROM media);

-- ledger_transactions.category_id (optional FK)
UPDATE ledger_transactions SET category_id = NULL
    WHERE category_id IS NOT NULL
    AND category_id NOT IN (SELECT id FROM ledger_categories);

-- ledger_transactions.account_id (optional FK)
UPDATE ledger_transactions SET account_id = NULL
    WHERE account_id IS NOT NULL
    AND account_id NOT IN (SELECT id FROM ledger_accounts);

-- ledger_transactions.to_account_id (optional FK)
UPDATE ledger_transactions SET to_account_id = NULL
    WHERE to_account_id IS NOT NULL
    AND to_account_id NOT IN (SELECT id FROM ledger_accounts);

-- ledger_deleted_items.book_id (optional FK)
UPDATE ledger_deleted_items SET book_id = NULL
    WHERE book_id IS NOT NULL
    AND book_id NOT IN (SELECT id FROM ledger_books);

-- ledger_recurring_rules.category_id (optional FK)
UPDATE ledger_recurring_rules SET category_id = NULL
    WHERE category_id IS NOT NULL
    AND category_id NOT IN (SELECT id FROM ledger_categories);

-- ledger_recurring_rules.account_id (optional FK)
UPDATE ledger_recurring_rules SET account_id = NULL
    WHERE account_id IS NOT NULL
    AND account_id NOT IN (SELECT id FROM ledger_accounts);

-- ledger_recurring_rules.to_account_id (optional FK)
UPDATE ledger_recurring_rules SET to_account_id = NULL
    WHERE to_account_id IS NOT NULL
    AND to_account_id NOT IN (SELECT id FROM ledger_accounts);

-- =====================================================================
-- HARD FIX: DELETE for required references (review before uncommenting!)
-- These match V24's ON DELETE CASCADE constraints.
-- Only uncomment if you're sure the orphan data should be removed.
-- =====================================================================

-- Albums without a library
-- DELETE FROM albums WHERE library_id NOT IN (SELECT id FROM shared_libraries);

-- Small albums without a library
-- DELETE FROM small_albums WHERE library_id NOT IN (SELECT id FROM shared_libraries);

-- Small albums without a parent album
-- DELETE FROM small_albums WHERE album_id IS NOT NULL AND album_id NOT IN (SELECT id FROM albums);

-- Media without a library
-- DELETE FROM media WHERE library_id NOT IN (SELECT id FROM shared_libraries);

-- Small album media junction orphans
-- DELETE FROM small_album_media WHERE small_album_id NOT IN (SELECT id FROM small_albums);
-- DELETE FROM small_album_media WHERE media_id NOT IN (SELECT id FROM media);

-- Comments without a library
-- DELETE FROM comments WHERE library_id NOT IN (SELECT id FROM shared_libraries);

-- Trash items without a library
-- DELETE FROM trash_items WHERE library_id NOT IN (SELECT id FROM shared_libraries);

-- Upload tasks without a library
-- DELETE FROM upload_tasks WHERE library_id NOT IN (SELECT id FROM shared_libraries);

-- Auth sessions without user or library
-- DELETE FROM auth_sessions WHERE user_id NOT IN (SELECT id FROM users);
-- DELETE FROM auth_sessions WHERE library_id NOT IN (SELECT id FROM shared_libraries);

-- Auth login challenges without user
-- DELETE FROM auth_login_challenges WHERE user_id NOT IN (SELECT id FROM users);

-- Auth remembered logins without user
-- DELETE FROM auth_remembered_logins WHERE user_id NOT IN (SELECT id FROM users);

-- Push device tokens without user
-- DELETE FROM push_device_tokens WHERE user_id NOT IN (SELECT id FROM users);

-- Push preferences without user
-- DELETE FROM push_preferences WHERE user_id NOT IN (SELECT id FROM users);

-- Shared library members without library or user
-- DELETE FROM shared_library_members WHERE library_id NOT IN (SELECT id FROM shared_libraries);
-- DELETE FROM shared_library_members WHERE user_id NOT IN (SELECT id FROM users);

-- Ledger tables without library
-- DELETE FROM ledger_books WHERE library_id NOT IN (SELECT id FROM shared_libraries);
-- DELETE FROM ledger_categories WHERE library_id NOT IN (SELECT id FROM shared_libraries);
-- DELETE FROM ledger_accounts WHERE library_id NOT IN (SELECT id FROM shared_libraries);
-- DELETE FROM ledger_transactions WHERE library_id NOT IN (SELECT id FROM shared_libraries);

-- Ledger internal orphans (CASCADE relationships)
-- DELETE FROM ledger_categories WHERE book_id NOT IN (SELECT id FROM ledger_books);
-- DELETE FROM ledger_accounts WHERE book_id NOT IN (SELECT id FROM ledger_books);
-- DELETE FROM ledger_transactions WHERE book_id NOT IN (SELECT id FROM ledger_books);

-- Additional ledger tables (V24 CASCADE relationships)
-- DELETE FROM ledger_budgets WHERE library_id NOT IN (SELECT id FROM shared_libraries);
-- DELETE FROM ledger_budgets WHERE book_id NOT IN (SELECT id FROM ledger_books);
-- DELETE FROM ledger_category_budgets WHERE library_id NOT IN (SELECT id FROM shared_libraries);
-- DELETE FROM ledger_category_budgets WHERE budget_id NOT IN (SELECT id FROM ledger_budgets);
-- DELETE FROM ledger_category_budgets WHERE category_id NOT IN (SELECT id FROM ledger_categories);
-- DELETE FROM ledger_recurring_rules WHERE library_id NOT IN (SELECT id FROM shared_libraries);
-- DELETE FROM ledger_recurring_rules WHERE book_id NOT IN (SELECT id FROM ledger_books);
-- DELETE FROM ledger_recurring_occurrences WHERE library_id NOT IN (SELECT id FROM shared_libraries);
-- DELETE FROM ledger_recurring_occurrences WHERE rule_id NOT IN (SELECT id FROM ledger_recurring_rules);
-- DELETE FROM ledger_recurring_occurrences WHERE transaction_id NOT IN (SELECT id FROM ledger_transactions);

COMMIT;

-- Verify: re-run audit after fix
\echo ''
\echo 'Fix complete. Re-run audit-orphans.sql to verify all counts are 0.'
\echo ''
