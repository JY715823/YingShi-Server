
---

# 4. Server `current-task.md`

```md
# Current Task: Stage 12.7-Hotfix - 上传媒体 / 导入到 App 联调支持

## 背景

Android 当前上传媒体和系统媒体导入到 App 链路不可用。底部加号上传提示后台加载但照片流不出现，系统媒体导入到 App 提示上传失败。Server 本阶段只做上传 / 导入契约所需的最小修正。

## 目标

1. 支持 Android 从 contentUri 读取后 multipart 上传。
2. 支持导入到 App 媒体库，不强依赖 postId。
3. 上传成功后返回完整 Media DTO。
4. 上传失败时返回清晰错误码和错误信息。
5. 查询照片流能返回新上传媒体。
6. 新上传媒体可以后续关联帖子、删除、恢复、评论。

## 检查范围

### 1. 上传接口

检查：

- multipart field name 是否和 Android 一致
- 是否支持 image/*
- 是否支持 video/*
- 是否支持无 postId 的 App 媒体库导入
- 是否保存 mimeType
- 是否保存文件大小
- 是否生成或返回 mediaUrl / thumbnailUrl / originalUrl / videoUrl
- 是否能通过真机 baseUrl 访问

### 2. 返回 DTO

Media DTO 至少检查：

- mediaId
- type
- mimeType
- thumbnailUrl
- mediaUrl
- originalUrl
- videoUrl
- width
- height
- duration
- createdAt
- postIds / relatedPosts

### 3. 照片流查询

检查：

- 新导入媒体是否进入照片流
- 孤立媒体是否允许进入照片流
- 删除 / 恢复状态是否正确过滤

### 4. 后续关系

检查：

- 新上传媒体能加入已有帖子
- 新上传媒体能发成新帖子
- 新上传媒体能删除 / 恢复
- 评论接口如支持媒体级评论，需要能处理无帖子归属媒体

## 不做内容

- 不做 OSS
- 不做转码
- 不做复杂后台任务
- 不改权限体系
- 不改回收站业务规则
- 不做 WebSocket / 推送

## 验收

1. Android 上传图片成功。
2. Android 上传视频成功或返回明确不支持错误。
3. 导入到 App 不强依赖 postId。
4. 上传成功返回完整 Media DTO。
5. 上传失败返回清晰错误信息。
6. 照片流查询能看到新上传媒体。
7. 新上传媒体可关联帖子。
8. 新上传媒体可删除 / 恢复。
9. 如修改 Server，mvnw test 通过。
## Stage 12.7 Hotfix Update - 2026-05-05

- Server upload remains a two-step local multipart flow: `POST /api/uploads/token`, then `POST /api/uploads/{uploadId}/file` with multipart field name `file`.
- Import-to-App does not require `postId`; upload success creates one orphan-capable `Media` row and `/api/media/feed` can return it.
- `application.yml` now raises multipart limits to `max-file-size: 200MB` and `max-request-size: 220MB`, fixing physical-device photo/video uploads that exceeded Spring defaults.
- Upload success returns a complete `MediaDto` with `mediaId`, type, mimeType, preview/media/original/video URLs, dimensions, duration, display time, and `postIds`.
- Server `mvnw test` passed after this update.
