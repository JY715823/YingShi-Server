-- V39: Add minimum supported version code for forced compatibility updates
-- FR-7: Allows server to force-update clients below a minimum version when API has breaking changes.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'app_releases' AND column_name = 'min_supported_version_code') THEN
        ALTER TABLE app_releases ADD COLUMN min_supported_version_code INTEGER;
    END IF;
END $$;

COMMENT ON COLUMN app_releases.min_supported_version_code IS 'Clients below this versionCode are forced to update (for API breaking changes). Null means no minimum enforced.';
