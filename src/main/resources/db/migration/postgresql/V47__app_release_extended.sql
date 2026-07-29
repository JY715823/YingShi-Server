-- R2-G-1: 扩展 app_releases 表字段，支持 APK 发布事务校验
-- 背景：原表已有 apk_sha256/file_size_bytes/min_supported_version_code/force_update (V33/V36/V39)
-- 本迁移补充：download_host（CDN 域名）和 signer_sha256（签名校验）

DO $$
BEGIN
    -- download_host: APK 下载的 CDN/对象存储域名，用于客户端拼接完整 URL
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'app_releases' AND column_name = 'download_host') THEN
        ALTER TABLE app_releases ADD COLUMN download_host VARCHAR(255);
    END IF;
    -- signer_sha256: APK 签名证书的 SHA-256，客户端可用于校验签名一致性（防伪造 APK）
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'app_releases' AND column_name = 'signer_sha256') THEN
        ALTER TABLE app_releases ADD COLUMN signer_sha256 VARCHAR(64);
    END IF;
END $$;

COMMENT ON COLUMN app_releases.download_host IS 'R2-G-1: APK 下载的 CDN/对象存储域名，与 download_url 拼接使用';
COMMENT ON COLUMN app_releases.signer_sha256 IS 'R2-G-12: APK 签名证书的 SHA-256 指纹，用于客户端校验签名一致性';
