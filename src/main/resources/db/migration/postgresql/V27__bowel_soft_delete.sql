-- V27: Bowel Event Soft Delete
-- Add deleted_at column to bowel_events table
-- to support soft delete instead of physical delete

ALTER TABLE bowel_events ADD COLUMN IF NOT EXISTS deleted_at BIGINT DEFAULT NULL;