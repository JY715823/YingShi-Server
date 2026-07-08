-- V24: Foreign key constraints for critical relationships
-- Adds referential integrity for the most important entity relationships.
-- Uses ON DELETE SET NULL for optional references and ON DELETE CASCADE
-- for strong ownership relationships.
--
-- NOTE: Table names reflect V5 renames (posts→small_albums, post_media→small_album_media,
--       comments.post_id→comments.small_album_id). The library parent table is shared_libraries.

-- =====================================================================
-- Library-scoped tables: library_id → shared_libraries(id) ON DELETE CASCADE
-- =====================================================================

ALTER TABLE albums
    ADD CONSTRAINT fk_albums_library FOREIGN KEY (library_id) REFERENCES shared_libraries(id) ON DELETE CASCADE;

ALTER TABLE small_albums
    ADD CONSTRAINT fk_small_albums_library FOREIGN KEY (library_id) REFERENCES shared_libraries(id) ON DELETE CASCADE;

ALTER TABLE media
    ADD CONSTRAINT fk_media_library FOREIGN KEY (library_id) REFERENCES shared_libraries(id) ON DELETE CASCADE;

ALTER TABLE comments
    ADD CONSTRAINT fk_comments_library FOREIGN KEY (library_id) REFERENCES shared_libraries(id) ON DELETE CASCADE;

ALTER TABLE trash_items
    ADD CONSTRAINT fk_trash_items_library FOREIGN KEY (library_id) REFERENCES shared_libraries(id) ON DELETE CASCADE;

ALTER TABLE upload_tasks
    ADD CONSTRAINT fk_upload_tasks_library FOREIGN KEY (library_id) REFERENCES shared_libraries(id) ON DELETE CASCADE;

-- =====================================================================
-- Small albums → album (ownership)
-- =====================================================================

ALTER TABLE small_albums
    ADD CONSTRAINT fk_small_albums_album FOREIGN KEY (album_id) REFERENCES albums(id) ON DELETE CASCADE;

-- =====================================================================
-- Small album media junction → small_albums and media
-- =====================================================================

ALTER TABLE small_album_media
    ADD CONSTRAINT fk_small_album_media_album FOREIGN KEY (small_album_id) REFERENCES small_albums(id) ON DELETE CASCADE;

ALTER TABLE small_album_media
    ADD CONSTRAINT fk_small_album_media_media FOREIGN KEY (media_id) REFERENCES media(id) ON DELETE CASCADE;

-- =====================================================================
-- Comments → small_albums and media (optional references, SET NULL)
-- =====================================================================

ALTER TABLE comments
    ADD CONSTRAINT fk_comments_small_album FOREIGN KEY (small_album_id) REFERENCES small_albums(id) ON DELETE SET NULL;

ALTER TABLE comments
    ADD CONSTRAINT fk_comments_media FOREIGN KEY (media_id) REFERENCES media(id) ON DELETE SET NULL;

-- =====================================================================
-- Auth tables → users / shared_libraries
-- =====================================================================

ALTER TABLE auth_sessions
    ADD CONSTRAINT fk_auth_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE auth_sessions
    ADD CONSTRAINT fk_auth_sessions_library FOREIGN KEY (library_id) REFERENCES shared_libraries(id) ON DELETE CASCADE;

ALTER TABLE auth_login_challenges
    ADD CONSTRAINT fk_login_challenges_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE auth_remembered_logins
    ADD CONSTRAINT fk_remembered_logins_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- =====================================================================
-- Push tables → users
-- =====================================================================

ALTER TABLE push_device_tokens
    ADD CONSTRAINT fk_push_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE push_preferences
    ADD CONSTRAINT fk_push_prefs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- =====================================================================
-- Shared library members
-- =====================================================================

ALTER TABLE shared_library_members
    ADD CONSTRAINT fk_shared_members_library FOREIGN KEY (library_id) REFERENCES shared_libraries(id) ON DELETE CASCADE;

ALTER TABLE shared_library_members
    ADD CONSTRAINT fk_shared_members_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- =====================================================================
-- Ledger tables → shared_libraries
-- =====================================================================

ALTER TABLE ledger_books
    ADD CONSTRAINT fk_ledger_books_library FOREIGN KEY (library_id) REFERENCES shared_libraries(id) ON DELETE CASCADE;

ALTER TABLE ledger_categories
    ADD CONSTRAINT fk_ledger_categories_library FOREIGN KEY (library_id) REFERENCES shared_libraries(id) ON DELETE CASCADE;

ALTER TABLE ledger_accounts
    ADD CONSTRAINT fk_ledger_accounts_library FOREIGN KEY (library_id) REFERENCES shared_libraries(id) ON DELETE CASCADE;

ALTER TABLE ledger_transactions
    ADD CONSTRAINT fk_ledger_transactions_library FOREIGN KEY (library_id) REFERENCES shared_libraries(id) ON DELETE CASCADE;

ALTER TABLE ledger_budgets
    ADD CONSTRAINT fk_ledger_budgets_library FOREIGN KEY (library_id) REFERENCES shared_libraries(id) ON DELETE CASCADE;

ALTER TABLE ledger_category_budgets
    ADD CONSTRAINT fk_ledger_cat_budgets_library FOREIGN KEY (library_id) REFERENCES shared_libraries(id) ON DELETE CASCADE;

ALTER TABLE ledger_recurring_rules
    ADD CONSTRAINT fk_ledger_recur_rules_library FOREIGN KEY (library_id) REFERENCES shared_libraries(id) ON DELETE CASCADE;

ALTER TABLE ledger_recurring_occurrences
    ADD CONSTRAINT fk_ledger_recur_occ_library FOREIGN KEY (library_id) REFERENCES shared_libraries(id) ON DELETE CASCADE;

ALTER TABLE ledger_deleted_items
    ADD CONSTRAINT fk_ledger_deleted_items_library FOREIGN KEY (library_id) REFERENCES shared_libraries(id) ON DELETE CASCADE;

-- =====================================================================
-- Ledger internal relationships
-- =====================================================================

ALTER TABLE ledger_categories
    ADD CONSTRAINT fk_ledger_categories_book FOREIGN KEY (book_id) REFERENCES ledger_books(id) ON DELETE CASCADE;

ALTER TABLE ledger_accounts
    ADD CONSTRAINT fk_ledger_accounts_book FOREIGN KEY (book_id) REFERENCES ledger_books(id) ON DELETE CASCADE;

ALTER TABLE ledger_transactions
    ADD CONSTRAINT fk_ledger_transactions_book FOREIGN KEY (book_id) REFERENCES ledger_books(id) ON DELETE CASCADE;

ALTER TABLE ledger_transactions
    ADD CONSTRAINT fk_ledger_transactions_category FOREIGN KEY (category_id) REFERENCES ledger_categories(id) ON DELETE SET NULL;

ALTER TABLE ledger_transactions
    ADD CONSTRAINT fk_ledger_transactions_account FOREIGN KEY (account_id) REFERENCES ledger_accounts(id) ON DELETE SET NULL;

ALTER TABLE ledger_transactions
    ADD CONSTRAINT fk_ledger_transactions_to_account FOREIGN KEY (to_account_id) REFERENCES ledger_accounts(id) ON DELETE SET NULL;

ALTER TABLE ledger_budgets
    ADD CONSTRAINT fk_ledger_budgets_book FOREIGN KEY (book_id) REFERENCES ledger_books(id) ON DELETE CASCADE;

ALTER TABLE ledger_category_budgets
    ADD CONSTRAINT fk_ledger_cat_budgets_budget FOREIGN KEY (budget_id) REFERENCES ledger_budgets(id) ON DELETE CASCADE;

ALTER TABLE ledger_category_budgets
    ADD CONSTRAINT fk_ledger_cat_budgets_category FOREIGN KEY (category_id) REFERENCES ledger_categories(id) ON DELETE CASCADE;

ALTER TABLE ledger_recurring_rules
    ADD CONSTRAINT fk_ledger_recur_rules_book FOREIGN KEY (book_id) REFERENCES ledger_books(id) ON DELETE CASCADE;

ALTER TABLE ledger_recurring_rules
    ADD CONSTRAINT fk_ledger_recur_rules_category FOREIGN KEY (category_id) REFERENCES ledger_categories(id) ON DELETE SET NULL;

ALTER TABLE ledger_recurring_rules
    ADD CONSTRAINT fk_ledger_recur_rules_account FOREIGN KEY (account_id) REFERENCES ledger_accounts(id) ON DELETE SET NULL;

ALTER TABLE ledger_recurring_rules
    ADD CONSTRAINT fk_ledger_recur_rules_to_account FOREIGN KEY (to_account_id) REFERENCES ledger_accounts(id) ON DELETE SET NULL;

ALTER TABLE ledger_recurring_occurrences
    ADD CONSTRAINT fk_ledger_recur_occ_rule FOREIGN KEY (rule_id) REFERENCES ledger_recurring_rules(id) ON DELETE CASCADE;

-- transaction_id is NOT NULL, so use CASCADE instead of SET NULL
ALTER TABLE ledger_recurring_occurrences
    ADD CONSTRAINT fk_ledger_recur_occ_tx FOREIGN KEY (transaction_id) REFERENCES ledger_transactions(id) ON DELETE CASCADE;

-- =====================================================================
-- Ledger deleted items → ledger_books (optional, SET NULL)
-- =====================================================================

ALTER TABLE ledger_deleted_items
    ADD CONSTRAINT fk_ledger_deleted_items_book FOREIGN KEY (book_id) REFERENCES ledger_books(id) ON DELETE SET NULL;

-- =====================================================================
-- Upload tasks → media (optional, SET NULL)
-- =====================================================================

ALTER TABLE upload_tasks
    ADD CONSTRAINT fk_upload_tasks_media FOREIGN KEY (media_id) REFERENCES media(id) ON DELETE SET NULL;
