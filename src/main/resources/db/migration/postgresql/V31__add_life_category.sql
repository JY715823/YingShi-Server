-- V31: 给 media 表添加 life_category 字段，用于区分 life 模块的 PERSON/MEAL 分类
-- 背景：之前 life 媒体通过 album + post(small_album) + post_media 三层结构复用 photos 模块的表
-- 现在 life 媒体不再放进相册，需要 media 自身携带 category 信息
ALTER TABLE media ADD COLUMN IF NOT EXISTS life_category VARCHAR(20);

-- 给 upload_tasks 表也添加 life_category 字段，持久化上传时的 category
ALTER TABLE upload_tasks ADD COLUMN IF NOT EXISTS life_category VARCHAR(20);

-- 回填现有 life 媒体的 life_category：
-- 通过 small_album_media → small_albums → albums.systemKey 反查 category
UPDATE media m
SET life_category = CASE
    WHEN a.system_key = 'life.person' THEN 'PERSON'
    WHEN a.system_key = 'life.meal' THEN 'MEAL'
    ELSE NULL
END
FROM small_album_media sam
JOIN small_albums sa ON sam.small_album_id = sa.id
JOIN albums a ON sa.album_id = a.id
WHERE sam.media_id = m.id
  AND m.domain = 'life'
  AND a.system_key IN ('life.person', 'life.meal');

-- 回填 upload_tasks 的 life_category（通过 media_id 关联）
UPDATE upload_tasks t
SET life_category = m.life_category
FROM media m
WHERE t.media_id = m.id
  AND m.domain = 'life'
  AND m.life_category IS NOT NULL;
