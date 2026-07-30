-- V48: Add PURGING state to trash_items check constraint
-- The purge flow sets state to PURGING before async COS deletion,
-- but the original check constraint only allowed IN_TRASH, PENDING_CLEANUP, RESTORED.

ALTER TABLE trash_items DROP CONSTRAINT trash_items_state_check;
ALTER TABLE trash_items ADD CONSTRAINT trash_items_state_check
    CHECK (state IN ('IN_TRASH', 'PENDING_CLEANUP', 'RESTORED', 'PURGING'));
