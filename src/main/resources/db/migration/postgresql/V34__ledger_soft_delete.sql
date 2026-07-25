-- V34: Add deleted_at_millis to 6 ledger tables for soft delete propagation
-- ledger_transactions already has deleted_at_millis (V21 L85, nullable bigint)
-- ledger_deleted_items already has deleted_at_millis (V21 L155, NOT NULL, business field - original item deletion time, NOT row-level soft delete marker)
-- Only 6 tables need the new field: categories, accounts, budgets, category_budgets, recurring_rules, recurring_occurrences

ALTER TABLE ledger_categories ADD COLUMN IF NOT EXISTS deleted_at_millis bigint;
ALTER TABLE ledger_accounts ADD COLUMN IF NOT EXISTS deleted_at_millis bigint;
ALTER TABLE ledger_budgets ADD COLUMN IF NOT EXISTS deleted_at_millis bigint;
ALTER TABLE ledger_category_budgets ADD COLUMN IF NOT EXISTS deleted_at_millis bigint;
ALTER TABLE ledger_recurring_rules ADD COLUMN IF NOT EXISTS deleted_at_millis bigint;
ALTER TABLE ledger_recurring_occurrences ADD COLUMN IF NOT EXISTS deleted_at_millis bigint;
