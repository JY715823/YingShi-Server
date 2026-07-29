-- R2-D-1/2: 孤儿清理持久 cursor，支持全表分批扫描、扫描位置可恢复。
-- 注意: last_scanned_id 使用 VARCHAR(64) 以匹配 MediaEntity.id (String) 类型。
CREATE TABLE IF NOT EXISTS orphan_scan_cursor (
    id VARCHAR(64) PRIMARY KEY,
    last_scanned_updated_at TIMESTAMP WITH TIME ZONE,
    last_scanned_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
INSERT INTO orphan_scan_cursor (id, last_scanned_updated_at, last_scanned_id)
    VALUES ('default', NULL, NULL) ON CONFLICT DO NOTHING;

-- R2-D-3/4: 孤儿对象 quarantine 隔离表。扫描时只标记不删除，隔离 7 天（跨一个备份周期）后由 purgeQuarantined 真正删除。
CREATE TABLE IF NOT EXISTS orphan_quarantine (
    id BIGSERIAL PRIMARY KEY,
    media_id VARCHAR(255),
    object_key VARCHAR(512) NOT NULL,
    detected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    quarantine_until TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'QUARANTINED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(media_id, object_key)
);

CREATE INDEX IF NOT EXISTS idx_orphan_quarantine_status_until ON orphan_quarantine (status, quarantine_until);
