-- R1-B-3: Add monotonically increasing sync_sequence to chat row-level tables for /sync cursor.
-- Idempotent: uses IF NOT EXISTS so re-running is safe.

-- imported_chats
ALTER TABLE imported_chats ADD COLUMN IF NOT EXISTS sync_sequence BIGINT;
CREATE SEQUENCE IF NOT EXISTS seq_imported_chats_sync START 1;
CREATE INDEX IF NOT EXISTS idx_imported_chats_sync_seq ON imported_chats(sync_sequence);

-- imported_messages
ALTER TABLE imported_messages ADD COLUMN IF NOT EXISTS sync_sequence BIGINT;
CREATE SEQUENCE IF NOT EXISTS seq_imported_messages_sync START 1;
CREATE INDEX IF NOT EXISTS idx_imported_messages_sync_seq ON imported_messages(sync_sequence);

-- imported_participants
ALTER TABLE imported_participants ADD COLUMN IF NOT EXISTS sync_sequence BIGINT;
CREATE SEQUENCE IF NOT EXISTS seq_imported_participants_sync START 1;
CREATE INDEX IF NOT EXISTS idx_imported_participants_sync_seq ON imported_participants(sync_sequence);

-- imported_resources
ALTER TABLE imported_resources ADD COLUMN IF NOT EXISTS sync_sequence BIGINT;
CREATE SEQUENCE IF NOT EXISTS seq_imported_resources_sync START 1;
CREATE INDEX IF NOT EXISTS idx_imported_resources_sync_seq ON imported_resources(sync_sequence);

-- imported_message_search
ALTER TABLE imported_message_search ADD COLUMN IF NOT EXISTS sync_sequence BIGINT;
CREATE SEQUENCE IF NOT EXISTS seq_imported_message_search_sync START 1;
CREATE INDEX IF NOT EXISTS idx_imported_message_search_sync_seq ON imported_message_search(sync_sequence);
