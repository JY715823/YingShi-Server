-- V38: Upload idempotency key and notification dedup table
-- R3-DATA-003: Add idempotency key to upload_tasks for duplicate prevention
-- R3-DIST-002: Create notification dedup table for persistent dedup across restarts

-- 1. Upload idempotency key
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'upload_tasks' AND column_name = 'idempotency_key') THEN
        ALTER TABLE upload_tasks ADD COLUMN idempotency_key VARCHAR(128);
    END IF;
END $$;

-- Unique index for idempotency key (partial: only non-null values).
-- Scope includes the uploader so one partner cannot reuse/collide with the
-- other partner's token inside the same shared library.
CREATE UNIQUE INDEX IF NOT EXISTS idx_upload_tasks_idempotency_key
    ON upload_tasks (library_id, uploaded_by_user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- 2. Notification dedup table for persistent dedup across JVM restarts
CREATE TABLE IF NOT EXISTS notification_dedup (
    operation_key     VARCHAR(255) PRIMARY KEY,
    library_id        VARCHAR(48) NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notification_dedup_created
    ON notification_dedup (created_at);
