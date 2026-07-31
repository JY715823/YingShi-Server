-- V50: upload_tasks表添加exif_metadata JSONB列
-- 客户端提取全部EXIF字段后发送，存储在upload_tasks中，传递给MediaEntity
-- 避免服务端从COS下载文件提取EXIF的浪费

ALTER TABLE upload_tasks ADD COLUMN exif_metadata JSONB;
COMMENT ON COLUMN upload_tasks.exif_metadata IS '客户端提取的全部EXIF元数据（24个字段），传递给MediaEntity';
