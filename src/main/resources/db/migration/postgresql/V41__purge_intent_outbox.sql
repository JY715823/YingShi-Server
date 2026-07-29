-- V41: Purge intent outbox table for transactional object deletion
-- R3-TRASH-002: Decouple DB transaction from object storage deletion.
-- Within a DB transaction, only record the purge intent + mark item PURGING.
-- After commit, PurgeIntentProcessor asynchronously deletes objects with retries.
-- This prevents permanent object loss when DB transaction rolls back.

CREATE TABLE IF NOT EXISTS purge_intents (
    id              VARCHAR(64) PRIMARY KEY,
    trash_item_id   VARCHAR(64) NOT NULL,
    library_id      VARCHAR(64) NOT NULL,
    object_type     VARCHAR(32) NOT NULL,
    storage_path    VARCHAR(512),
    object_key      VARCHAR(512),
    media_id        VARCHAR(64),
    state           VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER NOT NULL DEFAULT 0,
    max_attempts    INTEGER NOT NULL DEFAULT 5,
    next_retry_at   TIMESTAMP WITH TIME ZONE,
    last_error      TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at     TIMESTAMP WITH TIME ZONE
);

COMMENT ON TABLE purge_intents IS 'Outbox table for transactional object storage deletion (R3-TRASH-002).';

COMMENT ON COLUMN purge_intents.object_type IS 'MEDIA, PREVIEW, COVER, or CHAT_RESOURCE.';

COMMENT ON COLUMN purge_intents.state IS 'PENDING, IN_PROGRESS, COMPLETED, FAILED.';

-- Index for processor scan: find pending/failed intents due for retry
CREATE INDEX IF NOT EXISTS idx_purge_intents_state_retry
    ON purge_intents (state, next_retry_at)
    WHERE state IN ('PENDING', 'IN_PROGRESS', 'FAILED');

-- Index for lookup by trash item (cascade queries)
CREATE INDEX IF NOT EXISTS idx_purge_intents_trash_item
    ON purge_intents (trash_item_id);

-- Index for library-scoped audit queries
CREATE INDEX IF NOT EXISTS idx_purge_intents_library
    ON purge_intents (library_id, created_at DESC);
