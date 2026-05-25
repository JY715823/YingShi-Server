# Trash API Contract

更新时间：2026-05-25

## 状态

- 已按当前 `yingshi-server` 代码同步
- Android `REAL` 模式已接入全部已实现 trash 能力

## 基础规则

- 基础路径：`/api/trash`
- 所有接口都要求 bearer auth
- 默认分页：`page=1`、`size=10`
- 排序：按 `deletedAtMillis` 倒序
- 当前已支持 `restore / remove / purge / undo-remove`

## Trash Item DTO

```json
{
  "trashItemId": "trash_001",
  "itemType": "mediaSystemDeleted",
  "state": "inTrash",
  "sourcePostId": null,
  "sourceMediaId": "media_001",
  "commentTargetMediaId": "media_001",
  "title": "海边散步",
  "previewInfo": "媒体已移入回收站",
  "deletedAtMillis": 1777412800000,
  "relatedPostIds": [],
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

- `postDeleted`
- `mediaRemoved`
- `mediaSystemDeleted`

### `state`

- `inTrash`
- `pendingCleanup`
- `restored`

## 1. `GET /api/trash/items`

查询参数：

- `itemType` 可选
- `page`
- `size`

响应 `data`：

```json
{
  "items": [],
  "page": 1,
  "size": 10,
  "totalElements": 0,
  "hasMore": false
}
```

## 2. `GET /api/trash/items/{trashItemId}`

响应 `data`：

```json
{
  "item": {
    "trashItemId": "trash_001",
    "itemType": "postDeleted",
    "state": "inTrash",
    "sourcePostId": "post_001",
    "sourceMediaId": null,
    "commentTargetMediaId": null,
    "title": "春日散步",
    "previewInfo": "帖子已移入回收站",
    "deletedAtMillis": 1777412800000,
    "relatedPostIds": ["post_001"],
    "relatedMediaIds": ["media_001", "media_002"]
  },
  "canRestore": true,
  "canMoveOutOfTrash": true,
  "pendingCleanup": null
}
```

## 3. `POST /api/trash/items/{trashItemId}/restore`

- 请求体：无
- 响应：返回一个 `TrashItemDto`

当前语义：

- `postDeleted`：恢复帖子、帖子评论和帖子媒体关系
- `mediaRemoved`：恢复帖子内媒体关系
- `mediaSystemDeleted`：恢复媒体本体及相关关系

## 4. `POST /api/trash/items/{trashItemId}/remove`

- 请求体：无
- 响应：返回 `PendingCleanupDto`

说明：

- `remove` 不是立即永久删除
- 它表示条目进入 `pendingCleanup`
- Android 会把这一阶段作为 `24h 可撤销`

## 5. `POST /api/trash/items/{trashItemId}/purge`

- 请求体：无
- 响应：返回被最终删除前的 `TrashItemDto`

当前后端行为：

- `postDeleted`
  - 删除 trash item
  - 删除帖子记录、帖子评论和帖子关系
  - 清理该帖子相关的 `mediaRemoved` 记录
  - 不删除全局媒体本体和物理文件
- `mediaRemoved`
  - 仅最终确认“关系删除”
  - 不删除媒体记录和物理文件
- `mediaSystemDeleted`
  - 删除 trash item
  - 删除媒体记录和媒体评论
  - 删除媒体拥有的本地 `original / preview / cover` 文件

安全约束：

- 只允许删除配置好的本地存储目录内文件
- 不删除目录
- 不删除其他媒体拥有的文件
- 若物理文件清理失败，请求失败，trash item 保留

## 6. `POST /api/trash/items/{trashItemId}/undo-remove`

- 请求体：无
- 响应：返回一个 `TrashItemDto`

说明：

- 只允许对 `pendingCleanup` 条目撤销
- 超过截止时间后返回 `UNDO_REMOVE_EXPIRED`

## 7. `GET /api/trash/pending-cleanup`

- 响应：返回 `List<PendingCleanupDto>`

## 与 Android 当前联调约定

- Android `REAL` 模式会直接映射三种 `itemType`
- 回收站详情页会使用 `sourceMediaId / relatedMediaIds` 拉取媒体文件预览
- 删除后哪些页面要刷新，当前由 Android 按 `itemType` 和 `related*Ids` 自己决定

## 错误码

- `TRASH_ITEM_NOT_FOUND`
- `RESTORE_CONFLICT`
- `REMOVE_FROM_TRASH_CONFLICT`
- `UNDO_REMOVE_EXPIRED`
- `DELETE_CONFLICT`
- `MEDIA_NOT_FOUND`
- `AUTH_UNAUTHORIZED`
- `SERVER_ERROR`
