# Trash API Draft

## Status
- Server 6 local-dev implementation target

## Purpose
Represent trash items for post delete, post-media relation delete, and media system delete, plus restore and undo-remove flows.

## Pagination
- query params:
  - `page`
  - `size`
- default `page=1`
- default `size=10`
- default order: latest `deletedAtMillis` first

## Endpoints

### `GET /api/trash/items`
- use case: list trash items
- auth: required
- query:
  - `itemType` optional
  - `page`
  - `size`

### `GET /api/trash/items/{trashItemId}`
- use case: fetch one trash detail
- auth: required

### `POST /api/trash/items/{trashItemId}/restore`
- use case: restore one trash item
- auth: required

Behavior:
- `postDeleted`: restore post visibility
- `mediaRemoved`: restore original `PostMedia` relation
- `mediaSystemDeleted`: restore media visibility across posts

### `POST /api/trash/items/{trashItemId}/remove`
- use case: move one trash item into pending cleanup
- auth: required

Response data:

```json
{
  "trashItemId": "trash_001",
  "removedAtMillis": 1777412900000,
  "undoDeadlineMillis": 1777499300000,
  "item": {
    "trashItemId": "trash_001",
    "itemType": "postDeleted",
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
- use case: undo one pending-cleanup action
- auth: required

### `GET /api/trash/pending-cleanup`
- use case: list placeholder pending-cleanup entries
- auth: required

## Item Type Values
- `postDeleted`
- `mediaRemoved`
- `mediaSystemDeleted`

## Error Code Placeholders
- `TRASH_ITEM_NOT_FOUND`
- `RESTORE_CONFLICT`
- `REMOVE_FROM_TRASH_CONFLICT`
- `UNDO_REMOVE_EXPIRED`
- `AUTH_UNAUTHORIZED`

## Notes
- pending cleanup is only a state placeholder in this stage
- no real cleanup worker runs after the undo window
