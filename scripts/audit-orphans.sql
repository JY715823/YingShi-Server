-- ============================================================
-- V24 Foreign Key Orphan Audit Script
-- ============================================================
-- Run this BEFORE deploying V24 migration to production.
-- All orphan_count values should be 0 for safe migration.
--
-- Usage:
--   psql -h localhost -U yingshi -d yingshi -f scripts/audit-orphans.sql
--   (or via docker exec: docker exec -i yingshi-postgres psql -U yingshi -d yingshi < scripts/audit-orphans.sql)
-- ============================================================

\echo ''
\echo '=== V24 Foreign Key Orphan Audit ==='
\echo ''

-- =====================================================================
-- Library-scoped tables (ON DELETE CASCADE in V24)
-- =====================================================================

\echo '--- albums.library_id -> shared_libraries.id ---'
SELECT COUNT(*) AS orphan_count FROM albums a
    LEFT JOIN shared_libraries sl ON a.library_id = sl.id
    WHERE sl.id IS NULL;

\echo '--- small_albums.library_id -> shared_libraries.id ---'
SELECT COUNT(*) AS orphan_count FROM small_albums sa
    LEFT JOIN shared_libraries sl ON sa.library_id = sl.id
    WHERE sl.id IS NULL;

\echo '--- media.library_id -> shared_libraries.id ---'
SELECT COUNT(*) AS orphan_count FROM media m
    LEFT JOIN shared_libraries sl ON m.library_id = sl.id
    WHERE sl.id IS NULL;

\echo '--- comments.library_id -> shared_libraries.id ---'
SELECT COUNT(*) AS orphan_count FROM comments c
    LEFT JOIN shared_libraries sl ON c.library_id = sl.id
    WHERE sl.id IS NULL;

\echo '--- trash_items.library_id -> shared_libraries.id ---'
SELECT COUNT(*) AS orphan_count FROM trash_items t
    LEFT JOIN shared_libraries sl ON t.library_id = sl.id
    WHERE sl.id IS NULL;

\echo '--- upload_tasks.library_id -> shared_libraries.id ---'
SELECT COUNT(*) AS orphan_count FROM upload_tasks ut
    LEFT JOIN shared_libraries sl ON ut.library_id = sl.id
    WHERE sl.id IS NULL;

-- =====================================================================
-- Small albums ownership (ON DELETE CASCADE in V24)
-- =====================================================================

\echo '--- small_albums.album_id -> albums.id ---'
SELECT COUNT(*) AS orphan_count FROM small_albums sa
    LEFT JOIN albums a ON sa.album_id = a.id
    WHERE a.id IS NULL AND sa.album_id IS NOT NULL;

-- =====================================================================
-- Junction table (ON DELETE CASCADE in V24)
-- =====================================================================

\echo '--- small_album_media.small_album_id -> small_albums.id ---'
SELECT COUNT(*) AS orphan_count FROM small_album_media sam
    LEFT JOIN small_albums sa ON sam.small_album_id = sa.id
    WHERE sa.id IS NULL;

\echo '--- small_album_media.media_id -> media.id ---'
SELECT COUNT(*) AS orphan_count FROM small_album_media sam
    LEFT JOIN media m ON sam.media_id = m.id
    WHERE m.id IS NULL;

-- =====================================================================
-- Comments optional references (ON DELETE SET NULL in V24)
-- =====================================================================

\echo '--- comments.small_album_id -> small_albums.id (optional, SET NULL) ---'
SELECT COUNT(*) AS orphan_count FROM comments c
    LEFT JOIN small_albums sa ON c.small_album_id = sa.id
    WHERE sa.id IS NULL AND c.small_album_id IS NOT NULL;

\echo '--- comments.media_id -> media.id (optional, SET NULL) ---'
SELECT COUNT(*) AS orphan_count FROM comments c
    LEFT JOIN media m ON c.media_id = m.id
    WHERE m.id IS NULL AND c.media_id IS NOT NULL;

-- =====================================================================
-- Auth tables (ON DELETE CASCADE in V24)
-- =====================================================================

\echo '--- auth_sessions.user_id -> users.id ---'
SELECT COUNT(*) AS orphan_count FROM auth_sessions s
    LEFT JOIN users u ON s.user_id = u.id
    WHERE u.id IS NULL;

\echo '--- auth_sessions.library_id -> shared_libraries.id ---'
SELECT COUNT(*) AS orphan_count FROM auth_sessions s
    LEFT JOIN shared_libraries sl ON s.library_id = sl.id
    WHERE sl.id IS NULL;

\echo '--- auth_login_challenges.user_id -> users.id ---'
SELECT COUNT(*) AS orphan_count FROM auth_login_challenges lc
    LEFT JOIN users u ON lc.user_id = u.id
    WHERE u.id IS NULL;

\echo '--- auth_remembered_logins.user_id -> users.id ---'
SELECT COUNT(*) AS orphan_count FROM auth_remembered_logins rl
    LEFT JOIN users u ON rl.user_id = u.id
    WHERE u.id IS NULL;

-- =====================================================================
-- Push tables (ON DELETE CASCADE in V24)
-- =====================================================================

\echo '--- push_device_tokens.user_id -> users.id ---'
SELECT COUNT(*) AS orphan_count FROM push_device_tokens pdt
    LEFT JOIN users u ON pdt.user_id = u.id
    WHERE u.id IS NULL;

\echo '--- push_preferences.user_id -> users.id ---'
SELECT COUNT(*) AS orphan_count FROM push_preferences pp
    LEFT JOIN users u ON pp.user_id = u.id
    WHERE u.id IS NULL;

-- =====================================================================
-- Shared library members (ON DELETE CASCADE in V24)
-- =====================================================================

\echo '--- shared_library_members.library_id -> shared_libraries.id ---'
SELECT COUNT(*) AS orphan_count FROM shared_library_members slm
    LEFT JOIN shared_libraries sl ON slm.library_id = sl.id
    WHERE sl.id IS NULL;

\echo '--- shared_library_members.user_id -> users.id ---'
SELECT COUNT(*) AS orphan_count FROM shared_library_members slm
    LEFT JOIN users u ON slm.user_id = u.id
    WHERE u.id IS NULL;

-- =====================================================================
-- Upload tasks optional reference (ON DELETE SET NULL in V24)
-- =====================================================================

\echo '--- upload_tasks.media_id -> media.id (optional, SET NULL) ---'
SELECT COUNT(*) AS orphan_count FROM upload_tasks ut
    LEFT JOIN media m ON ut.media_id = m.id
    WHERE m.id IS NULL AND ut.media_id IS NOT NULL;

-- =====================================================================
-- Ledger tables -> shared_libraries (ON DELETE CASCADE in V24)
-- =====================================================================

\echo '--- ledger_books.library_id -> shared_libraries.id ---'
SELECT COUNT(*) AS orphan_count FROM ledger_books lb
    LEFT JOIN shared_libraries sl ON lb.library_id = sl.id
    WHERE sl.id IS NULL;

\echo '--- ledger_categories.library_id -> shared_libraries.id ---'
SELECT COUNT(*) AS orphan_count FROM ledger_categories lc
    LEFT JOIN shared_libraries sl ON lc.library_id = sl.id
    WHERE sl.id IS NULL;

\echo '--- ledger_accounts.library_id -> shared_libraries.id ---'
SELECT COUNT(*) AS orphan_count FROM ledger_accounts la
    LEFT JOIN shared_libraries sl ON la.library_id = sl.id
    WHERE sl.id IS NULL;

\echo '--- ledger_transactions.library_id -> shared_libraries.id ---'
SELECT COUNT(*) AS orphan_count FROM ledger_transactions lt
    LEFT JOIN shared_libraries sl ON lt.library_id = sl.id
    WHERE sl.id IS NULL;

-- =====================================================================
-- Ledger internal relationships (key optional references)
-- =====================================================================

\echo '--- ledger_transactions.category_id -> ledger_categories.id (optional, SET NULL) ---'
SELECT COUNT(*) AS orphan_count FROM ledger_transactions lt
    LEFT JOIN ledger_categories lc ON lt.category_id = lc.id
    WHERE lc.id IS NULL AND lt.category_id IS NOT NULL;

\echo '--- ledger_transactions.account_id -> ledger_accounts.id (optional, SET NULL) ---'
SELECT COUNT(*) AS orphan_count FROM ledger_transactions lt
    LEFT JOIN ledger_accounts la ON lt.account_id = la.id
    WHERE la.id IS NULL AND lt.account_id IS NOT NULL;

\echo '--- ledger_transactions.to_account_id -> ledger_accounts.id (optional, SET NULL) ---'
SELECT COUNT(*) AS orphan_count FROM ledger_transactions lt
    LEFT JOIN ledger_accounts la ON lt.to_account_id = la.id
    WHERE la.id IS NULL AND lt.to_account_id IS NOT NULL;

\echo '--- ledger_deleted_items.book_id -> ledger_books.id (optional, SET NULL) ---'
SELECT COUNT(*) AS orphan_count FROM ledger_deleted_items ldi
    LEFT JOIN ledger_books lb ON ldi.book_id = lb.id
    WHERE lb.id IS NULL AND ldi.book_id IS NOT NULL;

-- =====================================================================
-- Additional ledger tables (V24 FK constraints)
-- =====================================================================

\echo '--- ledger_budgets.library_id -> shared_libraries.id ---'
SELECT COUNT(*) AS orphan_count FROM ledger_budgets lb
    LEFT JOIN shared_libraries sl ON lb.library_id = sl.id
    WHERE sl.id IS NULL;

\echo '--- ledger_budgets.book_id -> ledger_books.id ---'
SELECT COUNT(*) AS orphan_count FROM ledger_budgets lb
    LEFT JOIN ledger_books b ON lb.book_id = b.id
    WHERE b.id IS NULL;

\echo '--- ledger_category_budgets.library_id -> shared_libraries.id ---'
SELECT COUNT(*) AS orphan_count FROM ledger_category_budgets lcb
    LEFT JOIN shared_libraries sl ON lcb.library_id = sl.id
    WHERE sl.id IS NULL;

\echo '--- ledger_category_budgets.budget_id -> ledger_budgets.id ---'
SELECT COUNT(*) AS orphan_count FROM ledger_category_budgets lcb
    LEFT JOIN ledger_budgets lb ON lcb.budget_id = lb.id
    WHERE lb.id IS NULL;

\echo '--- ledger_category_budgets.category_id -> ledger_categories.id ---'
SELECT COUNT(*) AS orphan_count FROM ledger_category_budgets lcb
    LEFT JOIN ledger_categories lc ON lcb.category_id = lc.id
    WHERE lc.id IS NULL;

\echo '--- ledger_recurring_rules.library_id -> shared_libraries.id ---'
SELECT COUNT(*) AS orphan_count FROM ledger_recurring_rules lrr
    LEFT JOIN shared_libraries sl ON lrr.library_id = sl.id
    WHERE sl.id IS NULL;

\echo '--- ledger_recurring_rules.book_id -> ledger_books.id ---'
SELECT COUNT(*) AS orphan_count FROM ledger_recurring_rules lrr
    LEFT JOIN ledger_books lb ON lrr.book_id = lb.id
    WHERE lb.id IS NULL;

\echo '--- ledger_recurring_rules.category_id -> ledger_categories.id (optional, SET NULL) ---'
SELECT COUNT(*) AS orphan_count FROM ledger_recurring_rules lrr
    LEFT JOIN ledger_categories lc ON lrr.category_id = lc.id
    WHERE lc.id IS NULL AND lrr.category_id IS NOT NULL;

\echo '--- ledger_recurring_rules.account_id -> ledger_accounts.id (optional, SET NULL) ---'
SELECT COUNT(*) AS orphan_count FROM ledger_recurring_rules lrr
    LEFT JOIN ledger_accounts la ON lrr.account_id = la.id
    WHERE la.id IS NULL AND lrr.account_id IS NOT NULL;

\echo '--- ledger_recurring_rules.to_account_id -> ledger_accounts.id (optional, SET NULL) ---'
SELECT COUNT(*) AS orphan_count FROM ledger_recurring_rules lrr
    LEFT JOIN ledger_accounts la ON lrr.to_account_id = la.id
    WHERE la.id IS NULL AND lrr.to_account_id IS NOT NULL;

\echo '--- ledger_recurring_occurrences.library_id -> shared_libraries.id ---'
SELECT COUNT(*) AS orphan_count FROM ledger_recurring_occurrences lro
    LEFT JOIN shared_libraries sl ON lro.library_id = sl.id
    WHERE sl.id IS NULL;

\echo '--- ledger_recurring_occurrences.rule_id -> ledger_recurring_rules.id ---'
SELECT COUNT(*) AS orphan_count FROM ledger_recurring_occurrences lro
    LEFT JOIN ledger_recurring_rules lrr ON lro.rule_id = lrr.id
    WHERE lrr.id IS NULL;

\echo '--- ledger_recurring_occurrences.transaction_id -> ledger_transactions.id ---'
SELECT COUNT(*) AS orphan_count FROM ledger_recurring_occurrences lro
    LEFT JOIN ledger_transactions lt ON lro.transaction_id = lt.id
    WHERE lt.id IS NULL;

\echo ''
\echo '=== Audit complete. All orphan_count should be 0. ==='
\echo '=== If any count > 0, run fix-orphans.sql before V24 migration. ==='
\echo ''
