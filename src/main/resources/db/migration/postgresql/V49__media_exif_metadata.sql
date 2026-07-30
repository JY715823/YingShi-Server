-- V49: 添加EXIF元数据JSONB列, 支持灵活扩展相机拍摄参数
-- 设计意图: 用JSONB存储任意EXIF字段(aperture/shutter_speed/iso/focal_length/camera_make/camera_model/lens_model等),
-- 无需每次新增字段都做schema迁移. 旧媒体可通过后台任务补填.

ALTER TABLE media ADD COLUMN exif_metadata JSONB;
COMMENT ON COLUMN media.exif_metadata IS 'EXIF拍摄参数(JSONB): aperture, shutter_speed, iso, focal_length, camera_make, camera_model, lens_model等';
