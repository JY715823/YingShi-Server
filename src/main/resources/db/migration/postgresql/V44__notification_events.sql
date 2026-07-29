-- R2-A-1: Persistent notification event store with 30-day retention.
-- Replaces the in-memory ring buffer in SseEmitterRegistry so missed events
-- can be replayed even after a server restart.
-- Idempotent: uses IF NOT EXISTS so re-running is safe.

CREATE TABLE IF NOT EXISTS notification_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,
    library_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    type VARCHAR(64) NOT NULL,
    data JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW() + INTERVAL '30 days',
    UNIQUE(event_id, library_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_notification_events_library_user_created
    ON notification_events(library_id, user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notification_events_expires
    ON notification_events(expires_at);

-- Read waterline: tracks the last event each user has seen/read per library.
CREATE TABLE IF NOT EXISTS notification_read_waterline (
    user_id VARCHAR(255) NOT NULL,
    library_id VARCHAR(255) NOT NULL,
    last_read_event_id VARCHAR(255),
    last_read_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY(user_id, library_id)
);
