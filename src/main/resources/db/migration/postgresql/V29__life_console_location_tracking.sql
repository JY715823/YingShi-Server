-- V29: Life Console Location Tracking
-- Add latitude / longitude / location_label to media, bowel_events, upload_tasks
-- Used by FR-18 (location tracking backend) for GPS coordinates and reverse-geocoded labels.

ALTER TABLE media ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION DEFAULT NULL;
ALTER TABLE media ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION DEFAULT NULL;
ALTER TABLE media ADD COLUMN IF NOT EXISTS location_label VARCHAR(255) DEFAULT NULL;

ALTER TABLE bowel_events ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION DEFAULT NULL;
ALTER TABLE bowel_events ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION DEFAULT NULL;
ALTER TABLE bowel_events ADD COLUMN IF NOT EXISTS location_label VARCHAR(255) DEFAULT NULL;

ALTER TABLE upload_tasks ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION DEFAULT NULL;
ALTER TABLE upload_tasks ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION DEFAULT NULL;
ALTER TABLE upload_tasks ADD COLUMN IF NOT EXISTS location_label VARCHAR(255) DEFAULT NULL;
