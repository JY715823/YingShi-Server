# Trash API Contract

更新时间：2026-06-11

## 状态

- 已按当前 `yingshi-server` 代码同步
- Android `REAL` 模式已接入全部已实现 trash 能力

## 基础规则

- 基础路径：`/api/trash`
- 所有接口都要求 bearer auth
- 默认分页：`page=1`、`size=10`
- 排序：按 `deletedAtMillis` 倒序

## Trash Item DTO

```json
{
  "trashItemId": "trash_001",
  "itemType": "mediaSystemDeleted",
  "state": "inTrash",
  "sourceSmallAlbumId": null,
  "sourceMediaId": "media_001",
  "commentTargetMediaId": "media_001",
  "title": "海边散步",
  "previewInfo": "媒体已移入回收站",
  "deletedAtMillis": 1777412800000,
  "relatedSmallAlbumIds": [],
  "relatedMediaIds": ["media_001"],
  "sourceMediaType": "image",
  "sourceMediaWidth": 1440,
  "sourceMediaHeight": 1920,
  "sourceMediaAspectRatio": 0.75,
  "sourceMediaDurationMillis": null,
  "sourceMediaMimeType": "image/jpeg"
}
```

### `itemType`

- `largeAlbumDeleted`
- `smallAlbumDeleted`
- `mediaRemoved`
- `mediaSystemDeleted`

### `state`

- `inTrash`
- `pendingCleanup`
- `restored`

## Endpoints

### `GET /api/trash/items`

查询参数：

- `itemType` 可选
- `page`
- `size`

### `GET /api/trash/items/{trashItemId}`

Response `data`:

```json
{
  "item": {
    "trashItemId": "trash_001",
    "itemType": "smallAlbumDeleted",
    "state": "inTrash",
    "sourceSmallAlbumId": "post_001",
    "sourceMediaId": null,
    "commentTargetMediaId": null,
    "title": "春日散步",
    "previewInfo": "小相册已移入回收站",
    "deletedAtMillis": 1777412800000,
    "relatedSmallAlbumIds": ["post_001"],
    "relatedMediaIds": ["media_001", "media_002"]
  },
  "canRestore": true,
  "canMoveOutOfTrash": true,
  "pendingCleanup": null
}
```

### `POST /api/trash/items/{trashItemId}/restore`

当前语义：

- `largeAlbumDeleted`：恢复大相册本体，以及本次整组进入回收站的小相册
- `smallAlbumDeleted`：恢复小相册、小相册评论和小相册媒体关系
- `mediaRemoved`：恢复小相册内媒体关系
- `mediaSystemDeleted`：恢复媒体本体及相关关系

### `POST /api/trash/items/{trashItemId}/remove`

- 返回 `PendingCleanupDto`

### `POST /api/trash/items/{trashItemId}/purge`

当前后端行为：

- `largeAlbumDeleted`
  - 删除 trash item
  - 最终清理对应大相册记录
  - 最终清理本次一起删除的小相册记录与其评论、关系
  - 不删除全局媒体本体和物理文件
- `smallAlbumDeleted`
  - 删除 trash item
  - 删除小相册记录、小相册评论和小相册关系
  - 清理该小相册相关的 `mediaRemoved` 记录
  - 不删除全局媒体本体和物理文件
- `mediaRemoved`
  - 仅最终确认关系删除
  - 不删除媒体记录和物理文件
- `mediaSystemDeleted`
  - 删除 trash item
  - 删除媒体记录和媒体评论
  - 删除媒体拥有的本地 `original / preview / cover` 文件

### `POST /api/trash/items/{trashItemId}/undo-remove`

- 只允许对 `pendingCleanup` 条目撤销

### `GET /api/trash/pending-cleanup`

- returns `List<PendingCleanupDto>`

## 错误码

- `TRASH_ITEM_NOT_FOUND`
- `RESTORE_CONFLICT`
- `REMOVE_FROM_TRASH_CONFLICT`
- `UNDO_REMOVE_EXPIRED`
- `DELETE_CONFLICT`
- `MEDIA_NOT_FOUND`
- `AUTH_UNAUTHORIZED`
- `SERVER_ERROR`
