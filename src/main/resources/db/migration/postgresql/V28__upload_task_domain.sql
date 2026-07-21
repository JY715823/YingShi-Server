-- V28: Upload Task Domain
-- Add domain column to upload_tasks to track the domain
-- for file storage path isolation

ALTER TABLE upload_tasks ADD COLUMN IF NOT EXISTS domain VARCHAR(20) DEFAULT NULL;