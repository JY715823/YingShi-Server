-- V40: Add optimistic lock version columns to auth entities
-- R3-AUTH-001 / R3-AUTH-002: Enable CAS-based concurrent refresh token rotation
-- and remembered-login rotation to prevent concurrent consumption races.

-- 1. auth_sessions: add version column for @Version optimistic locking
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'auth_sessions' AND column_name = 'version') THEN
        ALTER TABLE auth_sessions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
    END IF;
END $$;

COMMENT ON COLUMN auth_sessions.version IS 'Optimistic lock version for concurrent refresh token rotation (R3-AUTH-002).';

-- 2. auth_remembered_logins: add version column for @Version optimistic locking
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'auth_remembered_logins' AND column_name = 'version') THEN
        ALTER TABLE auth_remembered_logins ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
    END IF;
END $$;

COMMENT ON COLUMN auth_remembered_logins.version IS 'Optimistic lock version for concurrent remembered-login rotation (R3-AUTH-002).';

-- 3. Index for session family revocation queries (revokeAllByUserAndLibrary)
-- Supports R3-AUTH-002 replay detection: when a refresh token replay is detected,
-- all active sessions for the same user+library are revoked.
CREATE INDEX IF NOT EXISTS idx_auth_sessions_user_library_revoked
    ON auth_sessions (user_id, library_id, revoked_at)
    WHERE revoked_at IS NULL;
