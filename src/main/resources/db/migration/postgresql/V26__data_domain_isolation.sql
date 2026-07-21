-- V26: Data Domain Isolation
-- Add domain column to albums, small_albums, and media tables
-- to support three-domain isolation: photo / life / chat

ALTER TABLE albums ADD COLUMN IF NOT EXISTS domain VARCHAR(20) DEFAULT 'photo' NOT NULL;
ALTER TABLE small_albums ADD COLUMN IF NOT EXISTS domain VARCHAR(20) DEFAULT 'photo' NOT NULL;
ALTER TABLE media ADD COLUMN IF NOT EXISTS domain VARCHAR(20) DEFAULT 'photo' NOT NULL;