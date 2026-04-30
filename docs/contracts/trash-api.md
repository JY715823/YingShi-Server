# Trash API Contract

## Status
- unified with current `yingshi-server` code
- local-dev usable

## Base Rules
- base path: `/api/trash`
- bearer auth required for all endpoints
- list pagination defaults to `page=1`, `size=10`
- list sort order is newest `deletedAtMillis` first
- current backend has no direct purge endpoint

## Trash Item DTO

```json
{
  "trashItemId": "trash_001",
  "itemType": "postDeleted",
  "state": "inTrash",
  "sourcePostId": "post_001",
  "sourceMediaId": null,
  "title": "Night Walk",
  "previewInfo": "Post deleted",
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
      "title": "Night Walk",
      "previewInfo": "Post deleted",
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
    "title": "Night Walk",
    "previewInfo": "Post deleted",
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
    "title": "Night Walk",
    "previewInfo": "Post deleted",
    "deletedAtMillis": 1777412800000,
    "relatedPostIds": ["post_001"],
    "relatedMediaIds": ["media_001", "media_002"]
  }
}
```

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
