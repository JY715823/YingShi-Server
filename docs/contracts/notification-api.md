# Notification API Contract

Updated: 2026-05-31

## Status

- the backend notification API is live in `yingshi-server`
- Android notification center, detail, read, and mark-all-read are consuming this API in `REAL` mode
- base path: `/api/notifications`
- all endpoints require bearer auth

## Current Notification Sources

The server currently materializes a merged notification feed from:

- comments
- small-album content updates
- trash state changes
- upload completion or cancellation

Current notification `type` values:

- `comment`
- `comment_edit`
- `comment_delete`
- `content_update`
- `delete_restore`
- `system`

## Notification DTO

```json
{
  "notificationId": "comment:comment_001",
  "type": "comment",
  "title": "Demo B commented on a small album",
  "body": "Looks great",
  "createdAtMillis": 1777416400000,
  "isRead": false,
  "targetSummary": "Night Walk",
  "targetType": "SMALL_ALBUM",
  "smallAlbumId": "post_001",
  "mediaId": null,
  "trashItemId": null
}
```

Field notes:

- `notificationId` is a stable logical event id used by read-state persistence
- `targetType` is currently things like `SMALL_ALBUM`, `MEDIA`, item-type names from trash, or `UPLOAD`
- `smallAlbumId`, `mediaId`, and `trashItemId` are nullable depending on the event

## Endpoints

### `GET /api/notifications`

- `limit` is optional
- returns newest first

### `GET /api/notifications/{notificationId}`

- returns a single `NotificationDto`

### `POST /api/notifications/{notificationId}/read`

- returns the same `NotificationDto`, but with `isRead = true`

### `POST /api/notifications/read-all`

Response `data`:

```json
{
  "success": true,
  "affectedCount": 12
}
```
