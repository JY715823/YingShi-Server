# API 契约总览

更新时间：2026-05-25

## 状态

- 当前文档描述的是 `yingshi-server` 与 Android `REAL` 模式的联调基线
- 照片主链路、回收站、上传主链路已经稳定可用
- 通知、头像、refresh-token、帖子列表和上传任务接口都已补齐

## 通用约定

- 成功响应：

```json
{
  "requestId": "req_123",
  "data": {},
  "page": {
    "page": 1,
    "pageSize": 20,
    "nextCursor": null,
    "hasMore": false
  }
}
```

- 失败响应：

```json
{
  "requestId": "req_123",
  "error": {
    "code": "SERVER_ERROR",
    "message": "Something went wrong",
    "details": null
  }
}
```

- JSON 字段使用 `camelCase`
- 资源 ID 使用字符串
- 时间字段使用毫秒时间戳
- 受保护接口统一使用 bearer auth

## 当前已实现接口

### Public

- `GET /api/health`
- `POST /api/auth/login`
- `POST /api/auth/refresh-token`

### Authenticated

- `GET /api/auth/me`
- `PATCH /api/auth/me/profile`
- `POST /api/auth/logout`
- `POST /api/auth/me/avatar`
- `GET /api/auth/avatar/{userId}`
- `GET /api/albums`
- `GET /api/albums/{albumId}/posts`
- `GET /api/posts`
- `GET /api/posts/{postId}`
- `POST /api/posts`
- `PATCH /api/posts/{postId}`
- `PATCH /api/posts/{postId}/cover`
- `PATCH /api/posts/{postId}/media-order`
- `POST /api/posts/{postId}/media`
- `DELETE /api/posts/{postId}`
- `DELETE /api/posts/{postId}/media/{mediaId}?deleteMode=directory|system`
- `GET /api/media/feed`
- `GET /api/media/files/{mediaId}?variant=original|preview|cover`
- `DELETE /api/media/{mediaId}`
- `GET /api/posts/{postId}/comments`
- `GET /api/media/{mediaId}/comments`
- `POST /api/posts/{postId}/comments`
- `POST /api/media/{mediaId}/comments`
- `PATCH /api/comments/{commentId}`
- `DELETE /api/comments/{commentId}`
- `GET /api/trash/items`
- `GET /api/trash/items/{trashItemId}`
- `POST /api/trash/items/{trashItemId}/restore`
- `POST /api/trash/items/{trashItemId}/remove`
- `POST /api/trash/items/{trashItemId}/purge`
- `POST /api/trash/items/{trashItemId}/undo-remove`
- `GET /api/trash/pending-cleanup`
- `POST /api/uploads/token`
- `POST /api/uploads/{uploadId}/file`
- `GET /api/uploads/{uploadId}`
- `POST /api/uploads/{uploadId}/confirm`
- `POST /api/uploads/{uploadId}/cancel`
- `GET /api/notifications`
- `GET /api/notifications/{notificationId}`
- `POST /api/notifications/{notificationId}/read`
- `POST /api/notifications/read-all`

## Android 对齐说明

- Android `REAL` 仓库已经对齐：
  - `GET /api/posts`
  - `POST /api/auth/refresh-token`
  - 上传任务 `status / confirm / cancel`
- 当前后端已提供、但 Android UI 还未接入的主要能力：
  - 头像上传 / 头像读取
  - 通知接口

## 关键联调说明

- 当前 Android 登录恢复完全依赖 `/api/auth/me`
- `/login` 和 `/me` 已附带共享空间和 `partner` 信息
- refresh-token 已可用，返回新的 access / refresh token bundle
- 媒体文件接口当前支持：
  - `variant=original`
  - `variant=preview`
  - `variant=cover`
- 媒体文件接口支持 range request，便于视频播放和局部读取
- 回收站已经支持完整恢复和永久删除语义
- 上传当前是“申请 token -> multipart 上传文件 -> 任务状态/收尾”的链路

## 分页说明

- `GET /api/media/feed`
  - 不带 `cursor / pageSize` 时返回简单列表
  - 带参数时返回 `page.nextCursor / page.hasMore`
- `GET /api/trash/items` 使用 `page / size`
- 评论列表使用 `page / size`
- `GET /api/notifications` 使用 `limit`

## 常见错误码

- `AUTH_INVALID_CREDENTIALS`
- `AUTH_TOKEN_EXPIRED`
- `AUTH_UNAUTHORIZED`
- `AUTH_SESSION_INVALID`
- `ALBUM_NOT_FOUND`
- `POST_NOT_FOUND`
- `MEDIA_NOT_FOUND`
- `COMMENT_NOT_FOUND`
- `DELETE_CONFLICT`
- `RESTORE_CONFLICT`
- `REMOVE_FROM_TRASH_CONFLICT`
- `UNDO_REMOVE_EXPIRED`
- `UPLOAD_ALREADY_COMPLETED`
- `UPLOAD_NOT_FOUND`
- `FORBIDDEN`
- `VALIDATION_ERROR`
- `SERVER_ERROR`
