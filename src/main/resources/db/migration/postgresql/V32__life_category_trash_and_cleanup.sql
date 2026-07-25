-- V32: P1-1 改造收尾 + P1-2 life 回收站字段
-- 背景：life 媒体现在通过 media.life_category 字段携带分类，不再依赖 album/post/post_media 三层关联。
-- 1) 给 trash_items 加 life_category 字段，用于区分 life 回收站（PERSON/MEAL）vs 照片回收站（null）
-- 2) 软删旧的 life domain album/post，让相册模块查询自动过滤掉它们（不影响 trash 恢复逻辑）
-- 3) 回填现有 life 媒体对应的 trash_items.life_category

-- 1. trash_items 加 life_category 字段
ALTER TABLE trash_items ADD COLUMN IF NOT EXISTS life_category VARCHAR(20);

-- 2. 回填现有 life 媒体对应 trash_items 的 life_category
--    通过 trash_items.source_media_id 关联 media 表，把 media.life_category 复制过来
UPDATE trash_items t
SET life_category = m.life_category
FROM media m
WHERE t.source_media_id = m.id
  AND m.domain = 'life'
  AND m.life_category IS NOT NULL
  AND t.life_category IS NULL;

-- 3. 软删旧的 life domain albums (systemKey='life.person' / 'life.meal')
--    用 NOW() 设 deleted_at，让 albumRepository 的 deleted_at IS NULL 查询自动过滤
UPDATE albums
SET deleted_at = NOW(),
    updated_at = NOW()
WHERE system_key IN ('life.person', 'life.meal')
  AND domain = 'life'
  AND deleted_at IS NULL;

-- 4. 软删旧的 life domain posts (月度小相册 + 任何 life post)
--    life posts 的 system_key 是月度字符串 (如 "2024-01"), domain='life'
UPDATE small_albums
SET deleted_at = NOW(),
    updated_at = NOW()
WHERE domain = 'life'
  AND deleted_at IS NULL;

-- 注意：small_album_media (post_media) 关联表不删，保留以便 TrashService 恢复时仍能找到原 relation。
-- 新的 life 媒体不会创建 post_media 关联（LifeConsoleService.addMedia 已重写），
-- 旧的 life 媒体如果被恢复，TrashService.restoreMediaSystemDeleted 会尝试重建 relation，
-- 但因为 post 已软删，restoreRelationOrder 会跳过（postRepository.findByIdAndLibraryIdAndDeletedAtIsNull 返回 empty），
-- 这是可接受的行为：恢复后 media.deletedAt=null 即可显示在今日痕迹中，relation 不再需要。
