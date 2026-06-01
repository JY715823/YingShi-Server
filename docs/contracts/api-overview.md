# API 契约总览

更新时间：2026-05-31

## 当前内容语义

- `album` 表示大相册
- `small album` 表示小相册
- 媒体可以被多个小相册复用
- 评论分为小相册评论和媒体评论

## 通用约定

- 成功响应使用：

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

- 失败响应使用：

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
- 时间字段使用毫秒时间戳
- 受保护接口统一使用 bearer auth

## 已实现接口

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
- `GET /api/albums/{albumId}/small-albums`
- `GET /api/small-albums`
- `GET /api/small-albums/{smallAlbumId}`
- `POST /api/small-albums`
- `PATCH /api/small-albums/{smallAlbumId}`
- `PATCH /api/small-albums/{smallAlbumId}/cover`
- `PATCH /api/small-albums/{smallAlbumId}/media-order`
- `POST /api/small-albums/{smallAlbumId}/media`
- `DELETE /api/small-albums/{smallAlbumId}`
- `DELETE /api/small-albums/{smallAlbumId}/media/{mediaId}?deleteMode=directory|system`
- `GET /api/media/feed`
- `GET /api/media/files/{mediaId}?variant=original|preview|cover`
- `DELETE /api/media/{mediaId}`
- `GET /api/small-albums/{smallAlbumId}/comments`
- `GET /api/media/{mediaId}/comments`
- `POST /api/small-albums/{smallAlbumId}/comments`
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

## Android REAL 已对齐能力

- 大相册页与小相册详情
- 照片流与媒体查看器
- 小相册评论与媒体评论
- 上传、加入小相册、小相册创建/编辑
- 回收站与通知中心

## 常见错误码

- `AUTH_INVALID_CREDENTIALS`
- `AUTH_TOKEN_EXPIRED`
- `AUTH_UNAUTHORIZED`
- `AUTH_SESSION_INVALID`
- `ALBUM_NOT_FOUND`
- `SMALL_ALBUM_NOT_FOUND`
- `SMALL_ALBUM_COVER_INVALID`
- `SMALL_ALBUM_MEDIA_ORDER_INVALID`
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
