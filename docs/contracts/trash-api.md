# Trash API Contract

## Status
- unified with current `yingshi-server` code
- local-dev usable

## Base Rules
- base path: `/api/trash`
- bearer auth required for all endpoints
- list pagination defaults to `page=1`, `size=10`
- list sort order is newest `deletedAtMillis` first
- current backend supports direct permanent delete through `purge`

## Trash Item DTO

```json
{
  "trashItemId": "trash_001",
  "itemType": "postDeleted",
  "state": "inTrash",
  "sourcePostId": "post_001",
  "sourceMediaId": null,
  "title": "春日散步",
  "previewInfo": "帖子已移入回收站",
  "deletedAtMillis": 1777412800000,
  "relatedPostIds": ["post_001"],
  "relatedMediaIds": ["media_001", "media_002"]
}
```

Item types:
- `postDeleted`
- `mediaRemoved`
- `mediaSystemDeleted`

State values:
- `inTrash`
- `pendingCleanup`
- `restored`

## Endpoints

### `GET /api/trash/items`

Query:
- `itemType` optional
- `page`
- `size`

Response data:

```json
{
  "items": [
    {
      "trashItemId": "trash_001",
      "itemType": "postDeleted",
      "state": "inTrash",
      "sourcePostId": "post_001",
      "sourceMediaId": null,
      "title": "春日散步",
      "previewInfo": "帖子已移入回收站",
      "deletedAtMillis": 1777412800000,
      "relatedPostIds": ["post_001"],
      "relatedMediaIds": ["media_001", "media_002"]
    }
  ],
  "page": 1,
  "size": 10,
  "totalElements": 1,
  "hasMore": false
}
```

### `GET /api/trash/items/{trashItemId}`

Response data:

```json
{
  "item": {
    "trashItemId": "trash_001",
    "itemType": "postDeleted",
    "state": "inTrash",
    "sourcePostId": "post_001",
    "sourceMediaId": null,
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

### `POST /api/trash/items/{trashItemId}/restore`

Request:
- no request body

Response:
- returns one `TrashItemDto`

### `POST /api/trash/items/{trashItemId}/remove`

Response data:

```json
{
  "trashItemId": "trash_001",
  "removedAtMillis": 1777412900000,
  "undoDeadlineMillis": 1777499300000,
  "item": {
    "trashItemId": "trash_001",
    "itemType": "postDeleted",
    "state": "pendingCleanup",
    "sourcePostId": "post_001",
    "sourceMediaId": null,
    "title": "春日散步",
    "previewInfo": "帖子待彻底移出回收站",
    "deletedAtMillis": 1777412800000,
    "relatedPostIds": ["post_001"],
    "relatedMediaIds": ["media_001", "media_002"]
  }
}
```

Notes:
- Android REAL mode maps `postDeleted` / `mediaRemoved` / `mediaSystemDeleted` directly from backend
- `remove` means move to `pendingCleanup`, and `undo-remove` is the 24h撤销入口
- Trash detail rows should be able to render original deleted content by using `sourceMediaId` and `relatedMediaIds` with `/api/media/files/{mediaId}`. Active lists still hide deleted media; this is a read-only trash preview path.

### `POST /api/trash/items/{trashItemId}/purge`

Permanently deletes an in-trash item.

Response:
- returns the deleted `TrashItemDto`

Behavior:
- `postDeleted`: deletes the trash item and the deleted post record / post relations / post comments. It does not delete media files because those media may still belong to the global media library or other posts.
- `mediaRemoved`: deletes only the trash item, making the relation removal final. It does not delete the media record or physical files.
- `mediaSystemDeleted`: deletes the trash item, media record, media comments, and the media's explicitly owned local-storage files.

Physical file cleanup for `mediaSystemDeleted`:
- deletes the media original under `local-storage/originals`
- deletes matching `preview-v2` and `cover-v1` files under `local-storage/previews`
- refuses paths outside configured local storage
- does not delete directories or files owned by other media

If any required physical deletion fails, the request fails and the trash item remains available.

### `POST /api/trash/items/{trashItemId}/undo-remove`

Request:
- no request body

Response:
- returns one `TrashItemDto`

### `GET /api/trash/pending-cleanup`

Response:
- returns `List<PendingCleanupDto>`

## Error Codes
- `TRASH_ITEM_NOT_FOUND`
- `RESTORE_CONFLICT`
- `REMOVE_FROM_TRASH_CONFLICT`
- `UNDO_REMOVE_EXPIRED`
- `AUTH_UNAUTHORIZED`
