# Trash API Draft

## Status
- Stage 11.6 draft only
- no live backend required
- current app still defaults to fake local trash state

## Purpose
Represent app-content trash entries, trash detail, restore, remove-from-trash, undo-remove, and 24h pending-cleanup placeholders.

## Auth
- all trash endpoints are draft-authenticated endpoints
- header convention:

```http
Authorization: Bearer <token>
```

## Pagination
- Stage 11.6 fixes trash list pagination to `page + size`
- default order: latest `deletedAtMillis` first

## Endpoints

### `GET /v1/trash/items`
- use case: trash list
- auth: required
- query draft:
  - `type` optional
  - `page`
  - `size`

Response item draft:

```json
{
  "trashItemId": "trash_001",
  "itemType": "postDeleted",
  "sourcePostId": "post_001",
  "sourceMediaId": null,
  "title": "Night Walk",
  "previewInfo": "Deleted at 2026-04-29 10:00",
  "deletedAtMillis": 1777412800000,
  "relatedPostIds": ["post_001"],
  "relatedMediaIds": ["media_001", "media_002"]
}
```

### `GET /v1/trash/items/{trashItemId}`
- use case: trash detail shell
- auth: required

Response draft:

```json
{
  "requestId": "req_trash_detail",
  "data": {
    "item": {
      "trashItemId": "trash_001",
      "itemType": "postDeleted",
      "sourcePostId": "post_001",
      "sourceMediaId": null,
      "title": "Night Walk",
      "previewInfo": "Deleted at 2026-04-29 10:00",
      "deletedAtMillis": 1777412800000,
      "relatedPostIds": ["post_001"],
      "relatedMediaIds": ["media_001", "media_002"]
    },
    "canRestore": true,
    "canMoveOutOfTrash": true,
    "pendingCleanup": null
  }
}
```

### `POST /v1/trash/items/{trashItemId}/restore`
- use case: restore one trash item
- auth: required

Request draft:

```json
{
  "restoreRelations": true,
  "operatorNote": null
}
```

Response draft:
- return restored item metadata as `TrashItemDto`

### `POST /v1/trash/items/{trashItemId}/remove`
- use case: move item out of trash into pending-cleanup state
- auth: required

Response draft:

```json
{
  "requestId": "req_trash_remove",
  "data": {
    "trashItemId": "trash_001",
    "removedAtMillis": 1777412900000,
    "undoDeadlineMillis": 1777499300000,
    "item": {
      "trashItemId": "trash_001",
      "itemType": "postDeleted",
      "sourcePostId": "post_001",
      "sourceMediaId": null,
      "title": "Night Walk",
      "previewInfo": "Deleted at 2026-04-29 10:00",
      "deletedAtMillis": 1777412800000,
      "relatedPostIds": ["post_001"],
      "relatedMediaIds": ["media_001", "media_002"]
    }
  }
}
```

### `POST /v1/trash/items/{trashItemId}/undo-remove`
- use case: undo pending cleanup within the placeholder 24h window
- auth: required

Response draft:
- return restored-in-trash item metadata as `TrashItemDto`

### `GET /v1/trash/pending-cleanup`
- use case: query entries that have been moved out of trash but are still undoable
- auth: required

Response draft:
- list of `PendingCleanupDto`

### `DELETE /v1/trash/items/{trashItemId}`
- use case: backend-side permanent cleanup placeholder
- auth: required
- Stage 11.6 app flow does not call this directly yet

## Field Notes
- `itemType` draft values:
  - `postDeleted`
  - `mediaRemoved`
  - `mediaSystemDeleted`
- post delete, post media relation delete, and media system delete do not share the same restore semantics
- pending cleanup is the draft state after "remove from trash" and before final cleanup
- undo window is currently documented as 24 hours, but remains a backend-policy placeholder

## Error Code Placeholders
- `TRASH_ITEM_NOT_FOUND`
- `TRASH_DETAIL_NOT_FOUND`
- `RESTORE_CONFLICT`
- `REMOVE_FROM_TRASH_CONFLICT`
- `UNDO_REMOVE_EXPIRED`
- `DELETE_NOT_ALLOWED`
- `AUTH_UNAUTHORIZED`
- `NOT_IMPLEMENTED`

## Stage 11.6 Draft-Only Notes
- current app still runs fake trash state and fake undo window logic only
- permanent delete worker / pending cleanup scheduler is not implemented
- shared-media restore conflict policy remains draft-only
