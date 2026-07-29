-- R2-F-3: shared library 固定时区
-- 背景：双机时区不同会得到不同"今天"，需要 shared library 绑定固定 zoneId
-- 现有库都是中国用户，默认回填 Asia/Shanghai

ALTER TABLE shared_libraries ADD COLUMN IF NOT EXISTS zone_id VARCHAR(64);

-- 默认回填 Asia/Shanghai（现有库都是中国用户）
UPDATE shared_libraries SET zone_id = 'Asia/Shanghai' WHERE zone_id IS NULL;

-- 后续不允许 NULL
ALTER TABLE shared_libraries ALTER COLUMN zone_id SET NOT NULL;

COMMENT ON COLUMN shared_libraries.zone_id IS 'R2-F-3: library 固定时区，life/today/history/delete-latest 都基于此时区计算"今天"，不再信任客户端 zoneId';
