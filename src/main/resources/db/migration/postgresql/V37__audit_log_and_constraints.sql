-- V37: Audit log table, expired session cleanup support, and FK constraint audit
-- R3-DATA-001 / R3-AUDIT

-- 1. Audit log table for critical operations
CREATE TABLE IF NOT EXISTS audit_logs (
    id              VARCHAR(48) PRIMARY KEY,
    actor_user_id   VARCHAR(48),
    library_id      VARCHAR(48),
    action          VARCHAR(64) NOT NULL,
    resource_type   VARCHAR(64),
    resource_id     VARCHAR(128),
    details         TEXT,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(256),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_library_created
    ON audit_logs (library_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_action_created
    ON audit_logs (action, created_at DESC);

-- 2. Index for expired auth session cleanup
CREATE INDEX IF NOT EXISTS idx_auth_sessions_refresh_expire
    ON auth_sessions (refresh_expire_at)
    WHERE refresh_expire_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_auth_sessions_revoked
    ON auth_sessions (revoked_at)
    WHERE revoked_at IS NOT NULL;

-- 3. FK constraint: users.default_library_id -> shared_libraries.id
-- R3-DATA-001 P1-2: Prevent dangling references
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_users_default_library'
        AND table_name = 'users'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT fk_users_default_library
            FOREIGN KEY (default_library_id) REFERENCES shared_libraries(id)
            ON DELETE RESTRICT;
    END IF;
END $$;
