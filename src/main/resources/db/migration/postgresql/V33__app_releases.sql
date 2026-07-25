-- V33: App 版本发布表
-- 背景：客户端启动时需要检查版本更新（无需登录），通过比较 versionCode 判断是否需要下载新版 APK。
-- 该表为全局表（无 library_id），因为 App 包体本身不绑定具体 library。

CREATE TABLE IF NOT EXISTS app_releases (
    id                  VARCHAR(48)   PRIMARY KEY,
    platform            VARCHAR(16)   NOT NULL,
    version_code        INTEGER       NOT NULL,
    version_name        VARCHAR(32)   NOT NULL,
    download_url        VARCHAR(512)  NOT NULL,
    update_description  VARCHAR(2000),
    force_update        BOOLEAN       NOT NULL DEFAULT FALSE,
    published           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP     NOT NULL,
    updated_at          TIMESTAMP     NOT NULL
);

-- 查询热点：按 platform + published 取最新一条，created_at DESC
CREATE INDEX IF NOT EXISTS idx_app_releases_platform_published_created
    ON app_releases (platform, published, created_at DESC);

-- 初始化 v1.0.0 发布记录（占位，download_url 后续由部署脚本更新为真实地址）
-- 这里先插入一条 published=false 的记录，避免 App 启动时误判有更新
INSERT INTO app_releases (id, platform, version_code, version_name, download_url, update_description, force_update, published, created_at, updated_at)
VALUES (
    'apprel_seed_v1_0_0',
    'android',
    1,
    '1.0',
    '/download/yingshi-v1.0.apk',
    '初版发布',
    FALSE,
    FALSE,
    NOW(),
    NOW()
)
ON CONFLICT (id) DO NOTHING;
