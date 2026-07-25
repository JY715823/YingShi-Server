-- V35: Add composite (library_id, updated_at) indexes for ledger sync performance
-- Optimizes findByLibraryIdAndUpdatedAtAfter queries used in queryChangesSince
-- ledger_transactions already has idx_ledger_transactions_library_updated (V21 L105-106)

CREATE INDEX IF NOT EXISTS idx_ledger_categories_library_updated
    ON ledger_categories (library_id, updated_at);

CREATE INDEX IF NOT EXISTS idx_ledger_accounts_library_updated
    ON ledger_accounts (library_id, updated_at);

CREATE INDEX IF NOT EXISTS idx_ledger_budgets_library_updated
    ON ledger_budgets (library_id, updated_at);

CREATE INDEX IF NOT EXISTS idx_ledger_category_budgets_library_updated
    ON ledger_category_budgets (library_id, updated_at);

CREATE INDEX IF NOT EXISTS idx_ledger_deleted_items_library_updated
    ON ledger_deleted_items (library_id, updated_at);

CREATE INDEX IF NOT EXISTS idx_ledger_recurring_rules_library_updated
    ON ledger_recurring_rules (library_id, updated_at);

CREATE INDEX IF NOT EXISTS idx_ledger_recurring_occurrences_library_updated
    ON ledger_recurring_occurrences (library_id, updated_at);
