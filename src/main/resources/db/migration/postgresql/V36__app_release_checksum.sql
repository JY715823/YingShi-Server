-- V36: Add APK integrity verification fields for R3-UPD-001
-- Allows clients to verify downloaded APK matches server-published checksum and size.

-- R3-DATA-001: Use IF NOT EXISTS for idempotent migration
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'app_releases' AND column_name = 'apk_sha256') THEN
        ALTER TABLE app_releases ADD COLUMN apk_sha256 VARCHAR(64);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'app_releases' AND column_name = 'file_size_bytes') THEN
        ALTER TABLE app_releases ADD COLUMN file_size_bytes BIGINT;
    END IF;
END $$;

COMMENT ON COLUMN app_releases.apk_sha256 IS 'SHA-256 hex digest of the APK file for client-side integrity verification';
COMMENT ON COLUMN app_releases.file_size_bytes IS 'APK file size in bytes for download progress and storage pre-check';
